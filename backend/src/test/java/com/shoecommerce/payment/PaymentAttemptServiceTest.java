package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.OwnershipPolicy;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccount;
import com.shoecommerce.identity.UserAccountRepository;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;

class PaymentAttemptServiceTest {
    @Test
    void providerFailureDoesNotPersistAnAttemptOrConsumeTheIdempotencyKey() {
        UUID accountId = UUID.randomUUID(), orderId = UUID.randomUUID(), locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-27T02:00:00Z");
        SessionPrincipal actor = mock(SessionPrincipal.class);
        CustomerOrder order = mock(CustomerOrder.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        InventoryReservationService reservations = mock(InventoryReservationService.class);
        LocationRepository locations = mock(LocationRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        OwnershipPolicy ownership = mock(OwnershipPolicy.class);
        AuditWriter audit = mock(AuditWriter.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        CustomerOrder.ItemFacts item = new CustomerOrder.ItemFacts(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), locationId, 1, "SKU", "42", "Black", 100_000, 100_000);
        CustomerOrder.PaymentFacts facts = new CustomerOrder.PaymentFacts(orderId, accountId, UUID.randomUUID(),
                List.of(item), true, false, 100_000, 0, 100_000, "VND", null);

        when(actor.publicId()).thenReturn(accountId);
        when(accounts.findByPublicIdForUpdate(accountId)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(attempts.findScopedOrderId(accountId, "retryable-key")).thenReturn(Optional.empty());
        when(orders.findLockedByPublicId(orderId)).thenReturn(Optional.of(order));
        when(order.paymentFacts()).thenReturn(facts);
        when(payments.findLockedByOrderId(orderId)).thenReturn(Optional.empty());
        when(attempts.findByOwnerAccountPublicIdAndIdempotencyKey(accountId, "retryable-key"))
                .thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attempts.findByPaymentAndStatus(any(Payment.class), any(PaymentAttempt.Status.class)))
                .thenReturn(Optional.empty());
        when(reservations.requireAdoptedForPayment(actor, List.of(item.reservationId()), now))
                .thenReturn(now.plusSeconds(600));
        when(provider.paymentUrl(any())).thenThrow(new PaymentProviderUnavailableException("missing config"));

        PaymentAttemptService service = new PaymentAttemptService(payments, attempts, orders, reservations,
                locations, authorization, ownership, audit, provider, Clock.fixed(now, ZoneOffset.UTC), accounts);

        assertThatThrownBy(() -> service.initiate(actor, orderId, "retryable-key"))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .hasMessage("Payment provider is unavailable");
        verify(attempts, never()).save(any());
        verify(audit, never()).append(any(), any(), any(), any(), any(), any(), any());
    }
}
