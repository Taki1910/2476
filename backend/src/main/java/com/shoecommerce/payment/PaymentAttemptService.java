package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.OwnershipPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccountRepository;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;
import com.shoecommerce.platform.api.BusinessConflictException;

@Service
public class PaymentAttemptService {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final LocationRepository locations;
    private final AuthorizationPolicy authorization;
    private final OwnershipPolicy ownership;
    private final AuditWriter audit;
    private final PaymentProvider provider;
    private final Clock clock;
    private final UserAccountRepository accounts;

    public PaymentAttemptService(PaymentRepository payments, PaymentAttemptRepository attempts,
            CustomerOrderRepository orders, InventoryReservationService reservations, LocationRepository locations,
            AuthorizationPolicy authorization, OwnershipPolicy ownership, AuditWriter audit,
            PaymentProvider provider, Clock clock, UserAccountRepository accounts) {
        this.payments = payments; this.attempts = attempts; this.orders = orders; this.reservations = reservations;
        this.locations = locations; this.authorization = authorization; this.ownership = ownership; this.audit = audit;
        this.provider = provider; this.clock = clock;
        this.accounts = accounts;
    }

    @Transactional
    public InitiationResult initiate(SessionPrincipal actor, UUID orderId, String idempotencyKey) {
        return initiate(actor, orderId, idempotencyKey, "127.0.0.1");
    }

    @Transactional
    public InitiationResult initiate(SessionPrincipal actor, UUID orderId, String idempotencyKey, String clientIp) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_INITIATE);
        validateKey(idempotencyKey);
        // ponytail: reuse the per-account command fence; separate key rows only if contention warrants it.
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow();
        UUID replayOrderId = attempts.findScopedOrderId(actor.publicId(), idempotencyKey).orElse(null);
        if (replayOrderId != null && !replayOrderId.equals(orderId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key is already used for another Order.");
        }

        CustomerOrder order = orders.findLockedByPublicId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        ownership.requireOwnership(actor, facts.ownerAccountId());
        Payment payment = payments.findLockedByOrderId(orderId).orElse(null);
        PaymentAttempt replay = attempts.findByOwnerAccountPublicIdAndIdempotencyKey(actor.publicId(), idempotencyKey).orElse(null);
        if (replay != null) return result(attempts.findLockedByPublicId(replay.publicId()).orElseThrow(), false);
        if (!facts.pendingPayment()) {
            throw new BusinessConflictException("ORDER_NOT_PAYABLE", "Order is not eligible for payment.");
        }
        if (facts.totalAmount() <= 0 || !"VND".equals(facts.currency())) {
            throw new IllegalStateException("Order monetary snapshot is invalid");
        }

        Instant now = clock.instant();
        if (payment == null) payment = payments.save(Payment.create(order, facts.currency(), now));
        if (attempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.PENDING).isPresent()) {
            throw new BusinessConflictException("PAYMENT_ALREADY_PENDING", "Order already has an active payment attempt.");
        }
        Instant expiresAt = reservations.requireAdoptedForPayment(actor, facts.reservationIds(), now);
        String merchantReference = UUID.randomUUID().toString().replace("-", "");
        PaymentAttempt attempt = PaymentAttempt.create(payment, actor.publicId(), idempotencyKey,
                BigDecimal.valueOf(facts.totalAmount()), merchantReference, normalizeIp(clientIp), now, expiresAt);
        String paymentUrl = provider.paymentUrl(request(attempt));
        attempts.save(attempt);

        Location location = locations.findByPublicId(facts.locationId())
                .orElseThrow(() -> new IllegalStateException("Order location not found"));
        audit.append(actor, "PAYMENT_ATTEMPT_CREATED", "PAYMENT_ATTEMPT", attempt.publicId(),
                location.branchId(), location.id(), Map.of(
                        "orderId", orderId, "provider", attempt.provider(),
                        "merchantReference", attempt.merchantTransactionReference(),
                        "amount", attempt.amount(), "currency", attempt.currency(),
                        "expiresAt", attempt.expiresAt()));
        return new InitiationResult(view(attempt), paymentUrl, true);
    }

    @Transactional(readOnly = true)
    public PaymentAttemptView readOwn(SessionPrincipal actor, UUID attemptId) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_INITIATE);
        PaymentAttempt attempt = attempts.findByPublicId(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt not found"));
        ownership.requireOwnership(actor, attempt.ownerAccountPublicId());
        return view(attempt);
    }

    @Transactional(readOnly = true)
    public UUID findAttemptIdForVerifiedReturn(String merchantReference) {
        return attempts.findByMerchantTransactionReference(merchantReference)
                .map(PaymentAttempt::publicId).orElse(null);
    }

    @Transactional
    public void cancelPendingForOwnedOrder(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        Payment payment = payments.findLockedByOrderId(orderId).orElse(null);
        if (payment == null) return;
        ownership.requireOwnership(actor, payment.ownerAccountPublicId());
        PaymentAttempt attempt = attempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.PENDING).orElse(null);
        if (attempt == null) return;
        attempt.cancel(clock.instant());
        Location location = locations.findByPublicId(payment.locationPublicId())
                .orElseThrow(() -> new IllegalStateException("Order location not found"));
        audit.append(actor, "PAYMENT_ATTEMPT_CANCELLED", "PAYMENT_ATTEMPT", attempt.publicId(),
                location.branchId(), location.id(), Map.of("orderId", orderId));
    }

    @Transactional
    public void expirePendingForOrder(UUID orderId, Instant now) {
        Payment payment = payments.findLockedByOrderId(orderId).orElse(null);
        if (payment == null) return;
        PaymentAttempt attempt = attempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.PENDING).orElse(null);
        if (attempt != null) attempt.expire(now);
    }

    @Transactional(readOnly = true)
    public void requireSucceededForFulfillment(UUID orderId) {
        if (attempts.countSucceededForOrder(orderId) != 1) {
            throw new BusinessConflictException("Order has no successful payment attempt");
        }
    }

    private InitiationResult result(PaymentAttempt attempt, boolean created) {
        return new InitiationResult(view(attempt), provider.paymentUrl(request(attempt)), created);
    }
    private static PaymentProvider.Request request(PaymentAttempt attempt) {
        return new PaymentProvider.Request(attempt.merchantTransactionReference(), attempt.amount().longValueExact(),
                attempt.clientIp(), attempt.createdAt(), attempt.expiresAt());
    }
    private static String normalizeIp(String value) {
        String ip = value == null ? "" : value.trim();
        if (ip.isEmpty() || ip.length() > 45 || !ip.matches("[0-9A-Fa-f:.]+")) return "127.0.0.1";
        return ip;
    }
    private static void validateKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        if (key.length() > 128) throw new IllegalArgumentException("Idempotency-Key exceeds 128 characters");
    }
    private static PaymentAttemptView view(PaymentAttempt attempt) {
        return new PaymentAttemptView(attempt.publicId(), attempt.orderPublicId(), attempt.provider(),
                attempt.merchantTransactionReference(), attempt.status(), attempt.amount(), attempt.currency(),
                attempt.createdAt(), attempt.expiresAt(), attempt.cancelledAt(), attempt.resolvedAt(),
                attempt.providerTransactionNo(), attempt.providerResponseCode(),
                attempt.providerTransactionStatus(), attempt.providerPaidAt());
    }

    public record InitiationResult(PaymentAttemptView attempt, String paymentUrl, boolean created) { }
    public record PaymentAttemptView(UUID id, UUID orderId, String provider, String merchantTransactionReference,
            String status, BigDecimal amount, String currency, Instant createdAt, Instant expiresAt,
            Instant cancelledAt, Instant resolvedAt, String providerTransactionNo,
            String providerResponseCode, String providerTransactionStatus, Instant providerPaidAt) { }
}
