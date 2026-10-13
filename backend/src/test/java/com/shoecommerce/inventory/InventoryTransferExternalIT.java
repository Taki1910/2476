package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.catalog.CatalogService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class InventoryTransferExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired InventoryReservationService reservations;
    @Autowired InventoryTransferService transfers;

    @Test
    void provesLifecycleIdempotencyProvenanceAndAllOrNothingReceipt() {
        Fixture fixture = fixture(7, 0, 2);
        InventoryTransferService.CreateCommand command = command(fixture, 3, 2);
        Balance firstSourceBefore = balance(fixture.firstVariant(), fixture.source());
        Balance secondSourceBefore = balance(fixture.secondVariant(), fixture.source());

        var draft = transfers.create(fixture.operations(), command, "create-transfer");
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.items()).hasSize(2).allSatisfy(line -> {
            assertThat(line.dispatchedQuantity()).isZero();
            assertThat(line.receivedQuantity()).isZero();
        });
        assertThat(balance(fixture.firstVariant(), fixture.source())).isEqualTo(firstSourceBefore);
        assertThat(balance(fixture.secondVariant(), fixture.source())).isEqualTo(secondSourceBefore);
        assertThat(balanceOrZero(fixture.firstVariant(), fixture.destination())).isEqualTo(new Balance(0, 0, 0));

        var createReplay = transfers.create(fixture.operations(), command, "create-transfer");
        assertThat(createReplay.transferId()).isEqualTo(draft.transferId());
        assertThat(createReplay.replayed()).isTrue();
        assertThatThrownBy(() -> transfers.create(fixture.operations(),
                command(fixture, 4, 2), "create-transfer"))
                .isInstanceOf(BusinessConflictException.class).extracting("code")
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThatThrownBy(() -> transfers.dispatch(fixture.operations(), draft.transferId(), "create-transfer"))
                .isInstanceOf(BusinessConflictException.class).extracting("code")
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");

        var dispatched = transfers.dispatch(fixture.operations(), draft.transferId(), "dispatch-transfer");
        assertThat(dispatched.status()).isEqualTo("DISPATCHED");
        assertThat(balance(fixture.firstVariant(), fixture.source())).isEqualTo(new Balance(4, 2, 2));
        assertThat(balance(fixture.secondVariant(), fixture.source())).isEqualTo(new Balance(5, 0, 5));
        assertThat(balance(fixture.firstVariant(), fixture.destination())).isEqualTo(new Balance(0, 0, 0));
        assertThat(movements("TRANSFER_OUT", draft.transferId())).isEqualTo(2);
        assertThat(transfers.dispatch(fixture.operations(), draft.transferId(), "dispatch-transfer").replayed()).isTrue();
        assertThat(movements("TRANSFER_OUT", draft.transferId())).isEqualTo(2);
        assertThatThrownBy(() -> transfers.dispatch(fixture.operations(), draft.transferId(), "another-dispatch"))
                .isInstanceOf(BusinessConflictException.class);

        var received = transfers.receive(fixture.operations(), draft.transferId(), "receive-transfer");
        assertThat(received.status()).isEqualTo("RECEIVED");
        assertThat(received.items()).allSatisfy(line -> assertThat(line.receivedQuantity())
                .isEqualTo(line.dispatchedQuantity()));
        assertThat(balance(fixture.firstVariant(), fixture.destination())).isEqualTo(new Balance(3, 0, 3));
        assertThat(balance(fixture.secondVariant(), fixture.destination())).isEqualTo(new Balance(2, 0, 2));
        assertThat(movements("TRANSFER_IN", draft.transferId())).isEqualTo(2);
        assertThat(transfers.receive(fixture.operations(), draft.transferId(), "receive-transfer").replayed()).isTrue();
        assertThatThrownBy(() -> transfers.receive(fixture.operations(), draft.transferId(), "another-receive"))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(balance(fixture.firstVariant(), fixture.destination())).isEqualTo(new Balance(3, 0, 3));
        assertIntegrity(draft.transferId());
    }

    @Test
    void rejectsInvalidLocationsInsufficientAvailableAndMissingScopes() {
        Fixture fixture = fixture(3, 0, 2);
        assertThatThrownBy(() -> transfers.create(fixture.operations(), new InventoryTransferService.CreateCommand(
                fixture.source(), fixture.source(), List.of(new InventoryTransferService.LineCommand(
                        fixture.firstVariant(), 1))), "same-location"))
                .isInstanceOf(BusinessConflictException.class).extracting("code")
                .isEqualTo("INVENTORY_TRANSFER_SAME_LOCATION");

        var draft = transfers.create(fixture.operations(), new InventoryTransferService.CreateCommand(
                fixture.source(), fixture.destination(), List.of(new InventoryTransferService.LineCommand(
                        fixture.firstVariant(), 2))), "reserved-draft");
        assertThatThrownBy(() -> transfers.dispatch(fixture.operations(), draft.transferId(), "reserved-dispatch"))
                .isInstanceOf(BusinessConflictException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(balance(fixture.firstVariant(), fixture.source())).isEqualTo(new Balance(3, 2, 1));

        UUID foreignBranch = scopes.createBranch(fixture.admin(), "F" + shortId(), "Foreign");
        UUID foreign = scopes.createLocation(fixture.admin(), foreignBranch, "F" + shortId(), "Foreign stockroom");
        assertThatThrownBy(() -> transfers.create(fixture.operations(), new InventoryTransferService.CreateCommand(
                foreign, fixture.destination(), List.of(new InventoryTransferService.LineCommand(
                        fixture.firstVariant(), 1))), "foreign-source"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> transfers.create(fixture.operations(), new InventoryTransferService.CreateCommand(
                fixture.source(), foreign, List.of(new InventoryTransferService.LineCommand(
                        fixture.firstVariant(), 1))), "foreign-destination"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> transfers.create(fixture.operations(), new InventoryTransferService.CreateCommand(
                UUID.randomUUID(), fixture.destination(), List.of(new InventoryTransferService.LineCommand(
                        fixture.firstVariant(), 1))), "missing-source"))
                .isInstanceOf(ResourceNotFoundException.class).extracting("code").isEqualTo("LOCATION_NOT_FOUND");

        jdbc.update("UPDATE org_location SET enabled=0 WHERE public_id=?", fixture.destination());
        assertThatThrownBy(() -> transfers.create(fixture.operations(), command(fixture, 1, 1), "disabled-destination"))
                .isInstanceOf(ResourceNotFoundException.class).extracting("code").isEqualTo("LOCATION_NOT_FOUND");

        Fixture disabledSource = fixture(1, 0, 0);
        jdbc.update("UPDATE org_location SET enabled=0 WHERE public_id=?", disabledSource.source());
        assertThatThrownBy(() -> transfers.create(disabledSource.operations(), command(disabledSource, 1, 1),
                "disabled-source"))
                .isInstanceOf(ResourceNotFoundException.class).extracting("code").isEqualTo("LOCATION_NOT_FOUND");
    }

    @Test
    void multiLineDispatchFailureRollsBackEveryLine() {
        Fixture fixture = fixture(5, 0, 0);
        adjustments.adjust(fixture.operations(), fixture.secondVariant(), fixture.source(), 1,
                "Make second line insufficient", "dispatch-rollback-fixture");
        var draft = transfers.create(fixture.operations(), command(fixture, 3, 2), "dispatch-rollback-create");
        Balance firstBefore = balance(fixture.firstVariant(), fixture.source());
        Balance secondBefore = balance(fixture.secondVariant(), fixture.source());

        assertThatThrownBy(() -> transfers.dispatch(fixture.operations(), draft.transferId(),
                "dispatch-rollback-command"))
                .isInstanceOf(BusinessConflictException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");

        assertThat(balance(fixture.firstVariant(), fixture.source())).isEqualTo(firstBefore);
        assertThat(balance(fixture.secondVariant(), fixture.source())).isEqualTo(secondBefore);
        assertThat(movements("TRANSFER_OUT", draft.transferId())).isZero();
        assertThat(transferStatus(draft.transferId())).isEqualTo("DRAFT");
    }

    @Test
    void multiLineReceiveFailureRollsBackEveryLine() {
        Fixture fixture = fixture(5, 0, 0);
        var draft = transfers.create(fixture.operations(), command(fixture, 1, 2), "receive-rollback-create");
        transfers.dispatch(fixture.operations(), draft.transferId(), "receive-rollback-dispatch");
        jdbc.update("""
                UPDATE inventory_balance SET on_hand=?
                WHERE variant_id=(SELECT id FROM catalog_product_variant WHERE public_id=?)
                  AND location_id=(SELECT id FROM org_location WHERE public_id=?)
                """, Long.MAX_VALUE, fixture.secondVariant(), fixture.destination());
        try {
            assertThatThrownBy(() -> transfers.receive(fixture.operations(), draft.transferId(),
                    "receive-rollback-command"))
                    .isInstanceOf(InvalidRequestException.class).extracting("code")
                    .isEqualTo("INVENTORY_QUANTITY_OVERFLOW");
            assertThat(balance(fixture.firstVariant(), fixture.destination())).isEqualTo(new Balance(0, 0, 0));
            assertThat(balance(fixture.secondVariant(), fixture.destination()))
                    .isEqualTo(new Balance(Long.MAX_VALUE, 0, Long.MAX_VALUE));
            assertThat(movements("TRANSFER_IN", draft.transferId())).isZero();
            assertThat(transferStatus(draft.transferId())).isEqualTo("DISPATCHED");
        } finally {
            jdbc.update("""
                    UPDATE inventory_balance SET on_hand=0
                    WHERE variant_id=(SELECT id FROM catalog_product_variant WHERE public_id=?)
                      AND location_id=(SELECT id FROM org_location WHERE public_id=?)
                    """, fixture.secondVariant(), fixture.destination());
        }
    }

    @Test
    void concurrentTransferAndReservationCannotOversellAndTwoTransfersShareTheSourceSafely() throws Exception {
        Fixture fixture = fixture(2, 0, 0);
        var reservationRace = transfers.create(fixture.operations(), new InventoryTransferService.CreateCommand(
                fixture.source(), fixture.destination(), List.of(new InventoryTransferService.LineCommand(
                        fixture.firstVariant(), 2))), "race-create");
        RaceResult firstRace = race(
                () -> attempt(() -> transfers.dispatch(fixture.operations(), reservationRace.transferId(), "race-dispatch")),
                () -> attempt(() -> reservations.reserve(fixture.customer(), fixture.firstVariant(), fixture.source(), 1)));
        assertThat(firstRace.successes()).isEqualTo(1);
        assertInvariant(fixture.firstVariant(), fixture.source());

        Fixture shared = fixture(5, 0, 0);
        var first = transfers.create(shared.operations(), oneLine(shared, shared.source(), shared.destination(), 3), "first-create");
        var second = transfers.create(shared.secondOperations(), oneLine(shared, shared.source(), shared.destination(), 3), "second-create");
        RaceResult secondRace = race(
                () -> attempt(() -> transfers.dispatch(shared.operations(), first.transferId(), "first-dispatch")),
                () -> attempt(() -> transfers.dispatch(shared.secondOperations(), second.transferId(), "second-dispatch")));
        assertThat(secondRace.successes()).isEqualTo(1);
        assertThat(balance(shared.firstVariant(), shared.source())).isEqualTo(new Balance(2, 0, 2));
        assertThat(movements("TRANSFER_OUT", first.transferId()) + movements("TRANSFER_OUT", second.transferId())).isOne();
        assertInvariant(shared.firstVariant(), shared.source());
    }

    @Test
    void oppositeDirectionAndDuplicateCommandsUseDeterministicLocksAndOneEffect() throws Exception {
        Fixture fixture = fixture(4, 4, 0);
        var aToB = transfers.create(fixture.operations(), oneLine(fixture, fixture.source(), fixture.destination(), 1), "ab-create");
        var bToA = transfers.create(fixture.secondOperations(), oneLine(fixture, fixture.destination(), fixture.source(), 1), "ba-create");
        RaceResult opposite = race(
                () -> attempt(() -> transfers.dispatch(fixture.operations(), aToB.transferId(), "ab-dispatch")),
                () -> attempt(() -> transfers.dispatch(fixture.secondOperations(), bToA.transferId(), "ba-dispatch")));
        assertThat(opposite.successes()).isEqualTo(2);
        assertThat(balance(fixture.firstVariant(), fixture.source())).isEqualTo(new Balance(3, 0, 3));
        assertThat(balance(fixture.firstVariant(), fixture.destination())).isEqualTo(new Balance(3, 0, 3));

        Fixture duplicate = fixture(2, 0, 0);
        var transfer = transfers.create(duplicate.operations(), oneLine(duplicate, duplicate.source(), duplicate.destination(), 1), "duplicate-create");
        RaceResult dispatch = race(
                () -> transfers.dispatch(duplicate.operations(), transfer.transferId(), "duplicate-dispatch"),
                () -> transfers.dispatch(duplicate.operations(), transfer.transferId(), "duplicate-dispatch"));
        assertThat(dispatch.successes()).isEqualTo(2);
        assertThat(movements("TRANSFER_OUT", transfer.transferId())).isOne();
        assertThat(balance(duplicate.firstVariant(), duplicate.source())).isEqualTo(new Balance(1, 0, 1));
        RaceResult receive = race(
                () -> transfers.receive(duplicate.operations(), transfer.transferId(), "duplicate-receive"),
                () -> transfers.receive(duplicate.operations(), transfer.transferId(), "duplicate-receive"));
        assertThat(receive.successes()).isEqualTo(2);
        assertThat(movements("TRANSFER_IN", transfer.transferId())).isOne();
        assertThat(balance(duplicate.firstVariant(), duplicate.destination())).isEqualTo(new Balance(1, 0, 1));
        assertIntegrity(transfer.transferId());
    }

    private Fixture fixture(long sourceStock, long destinationStock, long reserved) {
        String prefix = "T" + shortId();
        SessionPrincipal admin = bootstrapAdministrator(prefix.toLowerCase() + "-admin@example.com");
        String firstLogin = prefix.toLowerCase() + "-ops1@example.com";
        String secondLogin = prefix.toLowerCase() + "-ops2@example.com";
        String customerLogin = prefix.toLowerCase() + "-customer@example.com";
        UUID firstAccount = identities.createAccount(admin, firstLogin, PASSWORD, RoleCode.OPERATIONS);
        UUID secondAccount = identities.createAccount(admin, secondLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, customerLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branch = scopes.createBranch(admin, prefix, "Transfer branch");
        UUID source = scopes.createLocation(admin, branch, prefix + "A", "Source");
        UUID destination = scopes.createLocation(admin, branch, prefix + "B", "Destination");
        for (UUID account : List.of(firstAccount, secondAccount)) {
            scopes.setAssignment(admin, account, branch, source, true);
            scopes.setAssignment(admin, account, branch, destination, true);
        }
        SessionPrincipal operations = principal(firstLogin);
        SessionPrincipal secondOperations = principal(secondLogin);
        UUID product = catalog.createProduct(operations, "Transfer Runner " + prefix);
        UUID firstVariant = catalog.createVariant(operations, product, prefix + "-1", "41", "Black");
        UUID secondVariant = catalog.createVariant(operations, product, prefix + "-2", "42", "White");
        for (UUID variant : List.of(firstVariant, secondVariant)) {
            catalog.setPrice(operations, variant, 100_000);
            adjustments.adjust(operations, variant, source, sourceStock, "Transfer fixture", UUID.randomUUID().toString());
            if (destinationStock > 0) adjustments.adjust(operations, variant, destination, destinationStock,
                    "Transfer fixture", UUID.randomUUID().toString());
            catalog.publish(operations, variant);
        }
        SessionPrincipal customer = principal(customerLogin);
        if (reserved > 0) reservations.reserve(customer, firstVariant, source, reserved);
        return new Fixture(admin, operations, secondOperations, customer, branch, source, destination,
                firstVariant, secondVariant);
    }

    private InventoryTransferService.CreateCommand command(Fixture fixture, long first, long second) {
        return new InventoryTransferService.CreateCommand(fixture.source(), fixture.destination(), List.of(
                new InventoryTransferService.LineCommand(fixture.firstVariant(), first),
                new InventoryTransferService.LineCommand(fixture.secondVariant(), second)));
    }
    private InventoryTransferService.CreateCommand oneLine(Fixture fixture, UUID source, UUID destination, long quantity) {
        return new InventoryTransferService.CreateCommand(source, destination,
                List.of(new InventoryTransferService.LineCommand(fixture.firstVariant(), quantity)));
    }
    private RaceResult race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> left = executor.submit(() -> { ready.countDown(); go.await(); return succeed(first); });
            Future<Boolean> right = executor.submit(() -> { ready.countDown(); go.await(); return succeed(second); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return new RaceResult((left.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (right.get(20, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
    private static boolean succeed(Callable<?> action) {
        try { action.call(); return true; }
        catch (BusinessConflictException | IllegalStateException exception) { return false; }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static Object attempt(Runnable action) { action.run(); return null; }
    private Balance balance(UUID variant, UUID location) {
        return jdbc.queryForObject("""
                SELECT on_hand,reserved,on_hand-reserved available FROM inventory_balance
                WHERE variant_id=(SELECT id FROM catalog_product_variant WHERE public_id=?)
                  AND location_id=(SELECT id FROM org_location WHERE public_id=?)
                """, (rs, row) -> new Balance(rs.getLong("on_hand"), rs.getLong("reserved"),
                        rs.getLong("available")), variant, location);
    }
    private Balance balanceOrZero(UUID variant, UUID location) {
        List<Balance> found = jdbc.query("""
                SELECT on_hand,reserved,on_hand-reserved available FROM inventory_balance
                WHERE variant_id=(SELECT id FROM catalog_product_variant WHERE public_id=?)
                  AND location_id=(SELECT id FROM org_location WHERE public_id=?)
                """, (rs, row) -> new Balance(rs.getLong("on_hand"), rs.getLong("reserved"),
                        rs.getLong("available")), variant, location);
        return found.isEmpty() ? new Balance(0, 0, 0) : found.getFirst();
    }
    private int movements(String type, UUID transferId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_movement WHERE operation_type=? AND transfer_public_id=?",
                Integer.class, type, transferId);
    }
    private String transferStatus(UUID transferId) {
        return jdbc.queryForObject("SELECT status FROM inventory_transfer WHERE public_id=?", String.class, transferId);
    }
    private void assertInvariant(UUID variant, UUID location) {
        Balance value = balance(variant, location);
        assertThat(value.onHand()).isGreaterThanOrEqualTo(0);
        assertThat(value.reserved()).isGreaterThanOrEqualTo(0);
        assertThat(value.onHand()).isGreaterThanOrEqualTo(value.reserved());
        assertThat(value.available()).isEqualTo(value.onHand() - value.reserved());
    }
    private void assertIntegrity(UUID transferId) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_transfer_line lines
                JOIN inventory_transfer transfers ON transfers.id=lines.transfer_id
                WHERE transfers.public_id=? AND (lines.received_quantity>lines.dispatched_quantity
                    OR lines.dispatched_quantity>lines.requested_quantity)
                """, Integer.class, transferId)).isZero();
        int lineCount = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transfer_line lines JOIN inventory_transfer transfers ON transfers.id=lines.transfer_id WHERE transfers.public_id=?",
                Integer.class, transferId);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_stock_movement movements
                WHERE movements.operation_type IN ('TRANSFER_OUT','TRANSFER_IN')
                  AND movements.transfer_public_id=?
                """, Integer.class, transferId)).isEqualTo(lineCount * 2);
    }
    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES (?,?,?,'ENABLED',1,0,?,?)",
                id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id,role_id) SELECT accounts.id,roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id=? AND roles.code='ADMINISTRATOR'", id);
        return principal(login);
    }
    private SessionPrincipal principal(String login) {
        SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login);
        principal.eraseCredentials();
        return principal;
    }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 7).toUpperCase(); }
    private record Balance(long onHand, long reserved, long available) { }
    private record RaceResult(int successes) { }
    private record Fixture(SessionPrincipal admin, SessionPrincipal operations, SessionPrincipal secondOperations,
            SessionPrincipal customer, UUID branch, UUID source, UUID destination,
            UUID firstVariant, UUID secondVariant) { }
}
