package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.catalog.ProductVariant;
import com.shoecommerce.catalog.ProductVariantRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccount;
import com.shoecommerce.identity.UserAccountRepository;
import com.shoecommerce.platform.api.BusinessConflictException;

class InventoryAdjustmentServiceTest {
    @Test
    void rejectsAStaleManagerAdjustmentBeforeChangingStock() {
        Fixture fixture = new Fixture();
        InventoryBalance balance = InventoryBalance.create(fixture.variant, fixture.location, 8, fixture.now);
        when(fixture.balances.findLockedByVariantAndLocation(fixture.variant, fixture.location))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> fixture.service.adjustManaged(fixture.actor, fixture.variantId,
                fixture.locationId, 10, "Counted stock", "key-1", 7L))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("INVENTORY_BALANCE_CHANGED"));

        assertThat(balance.onHand()).isEqualTo(8);
        verify(fixture.balances, never()).save(any());
    }

    @Test
    void requiresNullExpectedVersionOnlyWhenTheBalanceDoesNotExist() {
        Fixture fixture = new Fixture();
        InventoryBalance balance = InventoryBalance.create(fixture.variant, fixture.location, 8, fixture.now);
        when(fixture.balances.findLockedByVariantAndLocation(fixture.variant, fixture.location))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> fixture.service.adjustManaged(fixture.actor, fixture.variantId,
                fixture.locationId, 10, "Counted stock", "key-2", null))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("INVENTORY_BALANCE_CHANGED"));
    }

    private static final class Fixture {
        private final Instant now = Instant.parse("2026-09-27T00:00:00Z");
        private final UUID variantId = UUID.randomUUID();
        private final UUID locationId = UUID.randomUUID();
        private final SessionPrincipal actor = mock(SessionPrincipal.class);
        private final ProductVariant variant = mock(ProductVariant.class);
        private final Location location = mock(Location.class);
        private final InventoryBalanceRepository balances = mock(InventoryBalanceRepository.class);
        private final StockMovementRepository movements = mock(StockMovementRepository.class);
        private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
        private final LocationRepository locations = mock(LocationRepository.class);
        private final UserAccountRepository accounts = mock(UserAccountRepository.class);
        private final InventoryAdjustmentService service;

        private Fixture() {
            when(actor.publicId()).thenReturn(UUID.randomUUID());
            when(variants.findLockedByPublicId(variantId)).thenReturn(Optional.of(variant));
            when(locations.findByPublicId(locationId)).thenReturn(Optional.of(location));
            when(location.enabled()).thenReturn(true);
            when(accounts.findByPublicIdForUpdate(actor.publicId())).thenReturn(Optional.of(mock(UserAccount.class)));
            service = new InventoryAdjustmentService(balances, movements, variants, locations, accounts,
                    mock(AuthorizationPolicy.class), mock(AuditWriter.class), Clock.fixed(now, ZoneOffset.UTC));
        }
    }
}
