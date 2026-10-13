package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;

class PaymentReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T01:00:00Z");

    @Test
    void queriesOutsideTransactionThenPersistsEvidenceBeforeApplyingExistingPaymentResult() {
        Fixture fixture = fixture();
        AtomicBoolean providerObservedTransaction = new AtomicBoolean(true);
        PaymentQueryProvider.Result providerResult = new PaymentQueryProvider.Result(
                PaymentQueryProvider.Outcome.CONFIRMED_PAID, "RESP", "00", "00", "998877", NOW,
                "a".repeat(64));
        when(fixture.provider.query(any())).thenAnswer(invocation -> {
            providerObservedTransaction.set(fixture.transactions.active.get());
            return providerResult;
        });
        when(fixture.results.apply(any())).thenAnswer(invocation -> {
            assertThat(fixture.command.get().evidenceRecorded()).isTrue();
            return VerifiedPaymentResultService.Result.APPLIED;
        });

        PaymentReconciliationService.View view = fixture.service.reconcile(
                fixture.actor, fixture.attemptId, "same-key");

        assertThat(providerObservedTransaction).isFalse();
        assertThat(view.previousState()).isEqualTo("PENDING");
        assertThat(view.currentState()).isEqualTo("SUCCEEDED");
        assertThat(view.outcome()).isEqualTo("RESOLVED_PAID");
        assertThat(view.resolved()).isTrue();
        verify(fixture.provider, times(1)).query(any());
    }

    @Test
    void sameActorKeyCannotBeReusedForAnotherAttempt() {
        Fixture fixture = fixture();
        PaymentReconciliationCommand existing = PaymentReconciliationCommand.create(
                UUID.randomUUID(), fixture.actor.publicId(), "same-key", "REQ123", NOW);
        when(fixture.commands.findByActorAccountPublicIdAndIdempotencyKey(fixture.actor.publicId(), "same-key"))
                .thenReturn(Optional.of(existing));
        when(fixture.commands.findLockedByPublicId(existing.publicId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> fixture.service.reconcile(fixture.actor, fixture.attemptId, "same-key"))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void concurrentFirstUseOfSameActorKeyResolvesToCommittedCommandInsteadOfLeakingDuplicateKey() {
        Fixture fixture = fixture();
        PaymentReconciliationCommand winner = PaymentReconciliationCommand.create(
                fixture.attemptId, fixture.actor.publicId(), "same-key", "REQWINNER", NOW);
        winner.record(new PaymentQueryProvider.Result(PaymentQueryProvider.Outcome.CONFIRMED_PAID,
                "RESP", "00", "00", "998877", NOW, "f".repeat(64)), NOW);
        winner.complete("PENDING", "SUCCEEDED", true, NOW);
        when(fixture.commands.findByActorAccountPublicIdAndIdempotencyKey(
                fixture.actor.publicId(), "same-key"))
                .thenReturn(Optional.empty(), Optional.of(winner), Optional.of(winner));
        when(fixture.commands.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("concurrent idempotency claim"));
        when(fixture.commands.findLockedByPublicId(winner.publicId())).thenReturn(Optional.of(winner));

        PaymentReconciliationService.View replay = fixture.service.reconcile(
                fixture.actor, fixture.attemptId, "same-key");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.outcome()).isEqualTo("RESOLVED_PAID");
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void suspiciousProviderStatusMovesPendingAttemptToReviewInsteadOfFailure() {
        Fixture fixture = fixture();
        when(fixture.attempt.status()).thenReturn("REVIEW_REQUIRED");
        when(fixture.provider.query(any())).thenReturn(new PaymentQueryProvider.Result(
                PaymentQueryProvider.Outcome.REVIEW_REQUIRED, "RESP", "00", "04", "998877", NOW,
                "d".repeat(64)));
        when(fixture.results.applyReview(any())).thenReturn(VerifiedPaymentResultService.Result.APPLIED);

        PaymentReconciliationService.View view = fixture.service.reconcile(
                fixture.actor, fixture.attemptId, "same-key");

        assertThat(view.outcome()).isEqualTo("STILL_REVIEW_REQUIRED");
        assertThat(view.resolved()).isFalse();
        verify(fixture.results).applyReview(any());
        verify(fixture.results, never()).apply(any());
    }

    @Test
    void sameKeyAfterProviderOutageReplaysSafeFailureWithoutAnotherQuery() {
        Fixture fixture = fixture();
        when(fixture.provider.query(any())).thenThrow(new PaymentProviderUnavailableException("timeout"));

        assertThatThrownBy(() -> fixture.service.reconcile(fixture.actor, fixture.attemptId, "same-key"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> fixture.service.reconcile(fixture.actor, fixture.attemptId, "same-key"))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        verify(fixture.provider, times(1)).query(any());
        verifyNoInteractions(fixture.results);
    }

    @Test
    void localApplyRetryUsesSavedEvidenceWithoutAnotherProviderQuery() {
        Fixture fixture = fixture();
        when(fixture.provider.query(any())).thenReturn(new PaymentQueryProvider.Result(
                PaymentQueryProvider.Outcome.CONFIRMED_PAID, "RESP", "00", "00", "998877", NOW,
                "b".repeat(64)));
        when(fixture.results.apply(any()))
                .thenThrow(new RuntimeException("simulated local SQL contention"))
                .thenReturn(VerifiedPaymentResultService.Result.APPLIED);

        assertThatThrownBy(() -> fixture.service.reconcile(fixture.actor, fixture.attemptId, "same-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated local SQL contention");

        PaymentReconciliationService.View retried = fixture.service.reconcile(
                fixture.actor, fixture.attemptId, "same-key");

        assertThat(retried.outcome()).isEqualTo("RESOLVED_PAID");
        verify(fixture.provider, times(1)).query(any());
        verify(fixture.results, times(2)).apply(any());
    }

    @Test
    void definitiveResultAlreadyAppliedByConcurrentWriterDoesNotDuplicateReconciliationAudit() {
        Fixture fixture = fixture();
        when(fixture.provider.query(any())).thenReturn(new PaymentQueryProvider.Result(
                PaymentQueryProvider.Outcome.CONFIRMED_PAID, "RESP", "00", "00", "998877", NOW,
                "e".repeat(64)));
        when(fixture.results.apply(any())).thenReturn(VerifiedPaymentResultService.Result.ALREADY_PROCESSED);

        fixture.service.reconcile(fixture.actor, fixture.attemptId, "same-key");

        verifyNoInteractions(fixture.audit);
    }

    private static Fixture fixture() {
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        PaymentReconciliationCommandRepository commands = mock(PaymentReconciliationCommandRepository.class);
        PaymentQueryProvider provider = mock(PaymentQueryProvider.class);
        VerifiedPaymentResultService results = mock(VerifiedPaymentResultService.class);
        LocationRepository locations = mock(LocationRepository.class);
        AuditWriter audit = mock(AuditWriter.class);
        SessionPrincipal actor = mock(SessionPrincipal.class);
        PaymentAttempt attempt = mock(PaymentAttempt.class);
        PaymentAttemptRepository.ReconciliationFacts facts = mock(PaymentAttemptRepository.ReconciliationFacts.class);
        Location location = mock(Location.class);
        UUID actorId = UUID.randomUUID(), attemptId = UUID.randomUUID(), orderId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID(), reservationId = UUID.randomUUID(), variantId = UUID.randomUUID();
        when(actor.publicId()).thenReturn(actorId);
        when(attempt.publicId()).thenReturn(attemptId);
        when(attempt.provider()).thenReturn("VNPAY");
        when(attempt.status()).thenReturn("SUCCEEDED");
        when(attempt.pending()).thenReturn(true);
        when(attempt.currency()).thenReturn("VND");
        when(attempt.amount()).thenReturn(BigDecimal.valueOf(125_000));
        when(attempt.merchantTransactionReference()).thenReturn("PAY123");
        when(attempt.createdAt()).thenReturn(NOW.minusSeconds(60));
        when(attempt.providerTransactionNo()).thenReturn(null);
        when(facts.getAttemptId()).thenReturn(attemptId);
        when(facts.getOrderId()).thenReturn(orderId);
        when(facts.getLocationId()).thenReturn(locationId);
        when(facts.getProvider()).thenReturn("VNPAY");
        when(facts.getStatus()).thenReturn("PENDING");
        when(facts.getCurrency()).thenReturn("VND");
        when(facts.getAmount()).thenReturn(BigDecimal.valueOf(125_000));
        when(facts.getMerchantReference()).thenReturn("PAY123");
        when(facts.getCreatedAt()).thenReturn(NOW.minusSeconds(60));
        when(facts.getProviderTransactionNo()).thenReturn(null);
        when(attempts.findReconciliationFacts(attemptId)).thenReturn(Optional.of(facts));
        when(attempts.findByPublicId(attemptId)).thenReturn(Optional.of(attempt));
        when(locations.findByPublicId(locationId)).thenReturn(Optional.of(location));
        when(location.id()).thenReturn(7L); when(location.branchId()).thenReturn(3L);
        AtomicReference<PaymentReconciliationCommand> command = new AtomicReference<>();
        when(commands.findByActorAccountPublicIdAndIdempotencyKey(actorId, "same-key"))
                .thenAnswer(invocation -> Optional.ofNullable(command.get()));
        when(commands.saveAndFlush(any())).thenAnswer(invocation -> { command.set(invocation.getArgument(0)); return command.get(); });
        when(commands.findLockedByPublicId(any())).thenAnswer(invocation -> Optional.ofNullable(command.get()));
        ImmediateTransactions transactions = new ImmediateTransactions();
        PaymentReconciliationService service = new PaymentReconciliationService(authorization, attempts,
                commands, provider, results, locations, audit, Clock.fixed(NOW, ZoneOffset.UTC), transactions);
        return new Fixture(service, provider, results, commands, audit, actor, attempt, attemptId, command, transactions);
    }

    private record Fixture(PaymentReconciliationService service, PaymentQueryProvider provider,
            VerifiedPaymentResultService results, PaymentReconciliationCommandRepository commands,
            AuditWriter audit, SessionPrincipal actor, PaymentAttempt attempt, UUID attemptId,
            AtomicReference<PaymentReconciliationCommand> command,
            ImmediateTransactions transactions) { }

    private static final class ImmediateTransactions implements PlatformTransactionManager {
        private final AtomicBoolean active = new AtomicBoolean();
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            active.set(true); return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { active.set(false); }
        @Override public void rollback(TransactionStatus status) { active.set(false); }
    }
}
