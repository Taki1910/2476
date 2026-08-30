package com.shoecommerce.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;

@Service
@Profile("test")
public class PaymentProviderEventService {
    private static final String EVENT_REUSE = "Provider event ID is already used with different result content";
    private static final String ORDER_CANCELLED = "Payment result cannot apply after Order cancellation";
    private static final String ATTEMPT_TERMINAL = "Payment attempt is already terminal";

    private final PaymentProviderEventRepository events;
    private final PaymentAttemptRepository attempts;
    private final PaymentRepository payments;
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final LocationRepository locations;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final Clock clock;

    public PaymentProviderEventService(PaymentProviderEventRepository events, PaymentAttemptRepository attempts,
            PaymentRepository payments, CustomerOrderRepository orders, InventoryReservationService reservations,
            LocationRepository locations, AuthorizationPolicy authorization, AuditWriter audit, Clock clock) {
        this.events = events; this.attempts = attempts; this.payments = payments; this.orders = orders;
        this.reservations = reservations; this.locations = locations; this.authorization = authorization;
        this.audit = audit; this.clock = clock;
    }

    @Transactional
    public ApplicationResult apply(SessionPrincipal actor, String providerEventId, UUID attemptId, PaymentProviderEvent.Outcome outcome) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_EVENT_APPLY);
        validateEventId(providerEventId);
        if (attemptId == null || outcome == null) throw new IllegalArgumentException("Payment result is incomplete");

        PaymentProviderEvent replay = events.findScopedForUpdate(actor.publicId(), providerEventId).orElse(null);
        if (replay != null) {
            UUID replayOrderId = attempts.findOrderPublicId(replay.paymentAttemptPublicId()).orElseThrow(() -> new IllegalStateException("Recorded Payment attempt not found"));
            if (!replay.matches(attemptId, outcome)) return ApplicationResult.conflict(view(replay, replayOrderId), EVENT_REUSE);
            return replay.applied()
                    ? ApplicationResult.replay(view(replay, replayOrderId))
                    : ApplicationResult.conflict(view(replay, replayOrderId), rejectionMessage(replay.rejectionReason()));
        }

        UUID orderId = attempts.findOrderPublicId(attemptId).orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        Location location = locations.findByPublicId(facts.locationId()).orElseThrow(() -> new IllegalStateException("Order location not found"));
        Payment payment = payments.findLockedByOrderId(orderId).orElseThrow(() -> new IllegalStateException("Payment not found"));
        PaymentAttempt attempt = attempts.findLockedByPublicId(attemptId).orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        Instant now = clock.instant();

        if (!attempt.pending() || !facts.pendingPayment()) {
            String reason = "CANCELLED".equals(attempt.status()) || "CANCELLED".equals(order.paymentStatus()) ? "ORDER_CANCELLED" : "ATTEMPT_TERMINAL";
            PaymentProviderEvent rejected = events.save(PaymentProviderEvent.rejected(actor.publicId(), providerEventId,
                    attemptId, outcome, attempt.status(), orderStatus(order), reason, now));
            return ApplicationResult.conflict(view(rejected, orderId), rejectionMessage(reason));
        }

        if (outcome == PaymentProviderEvent.Outcome.SUCCESS) {
            InventoryReservationService.Consumption consumption = reservations.consumeForSuccessfulPayment(actor, facts.reservationId());
            attempt.succeed(now);
            order.markPaid(now);
            PaymentProviderEvent event = events.save(PaymentProviderEvent.applied(actor.publicId(), providerEventId,
                    attemptId, outcome, attempt.status(), orderStatus(order), now));
            audit.appendIntegration(actor, "PAYMENT_SUCCEEDED", "PAYMENT_ATTEMPT", attemptId,
                    location.branchId(), location.id(), Map.of(
                            "orderId", orderId, "providerEventId", providerEventId,
                            "amount", attempt.amount(), "currency", attempt.currency(),
                            "quantity", consumption.quantity(),
                            "beforeOnHand", consumption.beforeOnHand(), "beforeReserved", consumption.beforeReserved(),
                            "afterOnHand", consumption.afterOnHand(), "afterReserved", consumption.afterReserved()));
            return ApplicationResult.created(view(event, orderId));
        }

        attempt.fail(now);
        PaymentProviderEvent event = events.save(PaymentProviderEvent.applied(actor.publicId(), providerEventId,
                attemptId, outcome, attempt.status(), orderStatus(order), now));
        audit.appendIntegration(actor, "PAYMENT_FAILED", "PAYMENT_ATTEMPT", attemptId,
                location.branchId(), location.id(), Map.of("orderId", orderId, "providerEventId", providerEventId,
                        "amount", attempt.amount(), "currency", attempt.currency()));
        return ApplicationResult.created(view(event, orderId));
    }

    private static String orderStatus(CustomerOrder order) { return order.paymentStatus(); }

    private static void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("providerEventId is required");
        if (eventId.length() > 128) throw new IllegalArgumentException("providerEventId exceeds 128 characters");
    }

    private static String rejectionMessage(String reason) { return "ORDER_CANCELLED".equals(reason) ? ORDER_CANCELLED : ATTEMPT_TERMINAL; }
    private static ProviderEventView view(PaymentProviderEvent event, UUID orderId) { return new ProviderEventView(event.providerEventId(), event.paymentAttemptPublicId(), event.attemptStatus(), orderId, event.orderStatus(), event.appliedAt()); }

    public record ProviderEventView(String providerEventId, UUID paymentAttemptId, String paymentAttemptStatus,
            UUID orderId, String orderStatus, Instant appliedAt) { }
    public record ApplicationResult(ProviderEventView event, boolean created, String conflict) {
        static ApplicationResult created(ProviderEventView event) { return new ApplicationResult(event, true, null); }
        static ApplicationResult replay(ProviderEventView event) { return new ApplicationResult(event, false, null); }
        static ApplicationResult conflict(ProviderEventView event, String conflict) { return new ApplicationResult(event, false, conflict); }
        public boolean accepted() { return conflict == null; }
    }
}
