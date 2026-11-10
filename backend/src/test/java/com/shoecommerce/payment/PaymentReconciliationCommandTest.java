package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PaymentReconciliationCommandTest {
    private static final Instant NOW = Instant.parse("2026-10-09T01:00:00Z");

    @Test
    void recordsVerifiedEvidenceBeforeCompletingLocalApply() {
        UUID attemptId = UUID.randomUUID();
        PaymentReconciliationCommand command = PaymentReconciliationCommand.create(
                attemptId, UUID.randomUUID(), "same-key", "REQ123", NOW);
        PaymentQueryProvider.Result provider = new PaymentQueryProvider.Result(
                PaymentQueryProvider.Outcome.CONFIRMED_PAID, "RESP123", "00", "00",
                "998877", NOW, "a".repeat(64));

        command.record(provider, NOW.plusSeconds(1));
        assertThat(command.evidenceRecorded()).isTrue();
        assertThat(command.result()).isEqualTo(provider);

        command.complete("PENDING", "SUCCEEDED", true, NOW.plusSeconds(2));
        assertThat(command.completed()).isTrue();
        assertThat(command.previousAttemptStatus()).isEqualTo("PENDING");
        assertThat(command.currentAttemptStatus()).isEqualTo("SUCCEEDED");
        assertThat(command.localTransitionApplied()).isTrue();
    }

    @Test
    void providerFailureIsTerminalForTheSameCommandIdentity() {
        PaymentReconciliationCommand command = PaymentReconciliationCommand.create(
                UUID.randomUUID(), UUID.randomUUID(), "same-key", "REQ123", NOW);

        command.providerUnavailable(NOW.plusSeconds(1));

        assertThat(command.providerUnavailable()).isTrue();
        assertThat(command.providerRequestId()).isEqualTo("REQ123");
    }

    @Test
    void completedOrMismatchedEvidenceCannotBeOverwritten() {
        PaymentReconciliationCommand mismatch = PaymentReconciliationCommand.create(
                UUID.randomUUID(), UUID.randomUUID(), "key-a", "REQ123", NOW);
        mismatch.mismatch(NOW.plusSeconds(1));
        PaymentQueryProvider.Result replacement = new PaymentQueryProvider.Result(
                PaymentQueryProvider.Outcome.CONFIRMED_PAID, "OTHER", "00", "00", "1", NOW,
                "c".repeat(64));
        assertThatThrownBy(() -> mismatch.record(replacement, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);

        PaymentReconciliationCommand completed = PaymentReconciliationCommand.create(
                UUID.randomUUID(), UUID.randomUUID(), "key-b", "REQ789", NOW);
        completed.record(new PaymentQueryProvider.Result(PaymentQueryProvider.Outcome.UNRESOLVED,
                "RESP789", "00", "01", null, null, "b".repeat(64)), NOW.plusSeconds(1));
        completed.complete("PENDING", "PENDING", false, NOW.plusSeconds(2));
        assertThatThrownBy(() -> completed.record(replacement, NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }
}
