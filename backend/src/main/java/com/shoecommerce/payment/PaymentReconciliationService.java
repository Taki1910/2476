package com.shoecommerce.payment;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class PaymentReconciliationService {
    private final AuthorizationPolicy authorization;
    private final PaymentAttemptRepository attempts;
    private final PaymentReconciliationCommandRepository commands;
    private final PaymentQueryProvider provider;
    private final VerifiedPaymentResultService paymentResults;
    private final LocationRepository locations;
    private final AuditWriter audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    PaymentReconciliationService(AuthorizationPolicy authorization, PaymentAttemptRepository attempts,
            PaymentReconciliationCommandRepository commands,
            PaymentQueryProvider provider, VerifiedPaymentResultService paymentResults,
            LocationRepository locations, AuditWriter audit, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.authorization = authorization; this.attempts = attempts;
        this.commands = commands; this.provider = provider; this.paymentResults = paymentResults;
        this.locations = locations; this.audit = audit; this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public View reconcile(SessionPrincipal actor, UUID attemptId, String rawKey) {
        String key = key(rawKey);
        Work work = prepareWork(actor, attemptId, key);
        if (work.view() != null) return work.view();
        if (work.dispatch()) {
            PaymentQueryProvider.Result result;
            try {
                result = provider.query(work.request());
            } catch (PaymentReconciliationResultMismatchException exception) {
                transactions.executeWithoutResult(status -> mismatch(work.commandId()));
                throw exception;
            } catch (PaymentProviderUnavailableException exception) {
                transactions.executeWithoutResult(status -> providerUnavailable(work.commandId()));
                throw exception;
            }
            transactions.executeWithoutResult(status -> record(work.commandId(), result));
        }
        return transactions.execute(status -> apply(actor, work.commandId()));
    }

    private Work prepareWork(SessionPrincipal actor, UUID attemptId, String key) {
        try {
            return transactions.execute(status -> prepare(actor, attemptId, key));
        } catch (DataIntegrityViolationException exception) {
            boolean claimed = Boolean.TRUE.equals(transactions.execute(status -> commands
                    .findByActorAccountPublicIdAndIdempotencyKey(actor.publicId(), key).isPresent()));
            if (!claimed) throw exception;
            return transactions.execute(status -> prepare(actor, attemptId, key));
        }
    }

    private Work prepare(SessionPrincipal actor, UUID attemptId, String key) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_RECONCILE);
        Context context = context(attemptId);
        authorization.requireLocationAccess(actor, context.location().publicId());
        PaymentReconciliationCommand known = commands
                .findByActorAccountPublicIdAndIdempotencyKey(actor.publicId(), key).orElse(null);
        if (known != null) {
            PaymentReconciliationCommand command = commands.findLockedByPublicId(known.publicId()).orElseThrow();
            if (!command.target(attemptId)) throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT",
                    "This idempotency key belongs to another payment reconciliation target.");
            if (command.completed()) return new Work(command.publicId(), false, null, view(command, context, true));
            if (command.mismatch()) throw mismatchConflict();
            if (command.evidenceRecorded()) return new Work(command.publicId(), false, null, null);
            if (command.providerUnavailable()) throw new PaymentProviderUnavailableException("previous QueryDr failed");
            throw new BusinessConflictException("PAYMENT_RECONCILIATION_IN_PROGRESS",
                    "This payment reconciliation request is already in progress.");
        }
        requireEligible(context.facts());
        PaymentReconciliationCommand command = commands.saveAndFlush(PaymentReconciliationCommand.create(
                attemptId, actor.publicId(), key, requestId(), clock.instant()));
        return new Work(command.publicId(), true, request(command, context.facts()), null);
    }

    private void record(UUID commandId, PaymentQueryProvider.Result result) {
        commands.findLockedByPublicId(commandId).orElseThrow().record(result, clock.instant());
    }
    private void mismatch(UUID commandId) {
        PaymentReconciliationCommand command = commands.findLockedByPublicId(commandId).orElseThrow();
        if (command.querying()) command.mismatch(clock.instant());
    }
    private void providerUnavailable(UUID commandId) {
        PaymentReconciliationCommand command = commands.findLockedByPublicId(commandId).orElseThrow();
        if (command.querying()) command.providerUnavailable(clock.instant());
    }

    private View apply(SessionPrincipal actor, UUID commandId) {
        PaymentReconciliationCommand command = commands.findLockedByPublicId(commandId).orElseThrow();
        Context context = context(command.paymentAttemptPublicId());
        authorization.requirePermission(actor, PermissionCode.PAYMENT_RECONCILE);
        authorization.requireLocationAccess(actor, context.location().publicId());
        if (command.completed()) return view(command, context, true);
        PaymentQueryProvider.Result queried = command.result();
        String previous = context.facts().getStatus();
        boolean applied = false;
        if (!context.reviewRequired()) {
            PaymentProvider.VerifiedResult verified = verified(context.facts(), queried);
            VerifiedPaymentResultService.Result result = switch (queried.outcome()) {
                case CONFIRMED_PAID, CONFIRMED_FAILED -> paymentResults.apply(verified);
                case REVIEW_REQUIRED -> paymentResults.applyReview(verified);
                case UNRESOLVED -> VerifiedPaymentResultService.Result.ALREADY_PROCESSED;
            };
            if (result == VerifiedPaymentResultService.Result.NOT_FOUND
                    || result == VerifiedPaymentResultService.Result.AMOUNT_MISMATCH) {
                throw mismatchConflict();
            }
            applied = result == VerifiedPaymentResultService.Result.APPLIED;
        }
        String current = context.reviewRequired() || queried.outcome() == PaymentQueryProvider.Outcome.UNRESOLVED
                ? previous : attempts.findByPublicId(context.facts().getAttemptId()).orElseThrow().status();
        command.complete(previous, current, applied, clock.instant());
        if (applied || queried.outcome() == PaymentQueryProvider.Outcome.UNRESOLVED
                || queried.outcome() == PaymentQueryProvider.Outcome.REVIEW_REQUIRED) {
            audit.append(actor, applied ? "PAYMENT_RECONCILIATION_APPLIED" : "PAYMENT_RECONCILIATION_RECORDED",
                    "PAYMENT_RECONCILIATION_COMMAND", command.publicId(), context.location().branchId(),
                    context.location().id(), Map.of("paymentAttemptId", context.facts().getAttemptId(),
                            "orderId", context.orderId(), "providerReference",
                            context.facts().getMerchantReference(), "providerOutcome", queried.outcome().name(),
                            "previousState", previous, "currentState", current));
        }
        return view(command, context, false);
    }

    private Context context(UUID attemptId) {
        if (attemptId == null) throw new ResourceNotFoundException("PAYMENT_ATTEMPT_NOT_FOUND", "Payment attempt not found.");
        PaymentAttemptRepository.ReconciliationFacts facts = attempts.findReconciliationFacts(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_ATTEMPT_NOT_FOUND", "Payment attempt not found."));
        Location location = locations.findByPublicId(facts.getLocationId()).orElseThrow();
        return new Context(facts, location);
    }

    private static void requireEligible(PaymentAttemptRepository.ReconciliationFacts facts) {
        if (!"VNPAY".equals(facts.getProvider())
                || !("PENDING".equals(facts.getStatus()) || "REVIEW_REQUIRED".equals(facts.getStatus()))) {
            throw new BusinessConflictException("PAYMENT_RECONCILIATION_NOT_ALLOWED",
                    "Only an ambiguous VNPAY payment attempt can be reconciled.");
        }
    }
    private static String key(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 128 characters.");
        }
        return value;
    }
    private static String requestId() { return UUID.randomUUID().toString().replace("-", ""); }
    private PaymentQueryProvider.Request request(PaymentReconciliationCommand command,
            PaymentAttemptRepository.ReconciliationFacts facts) {
        return new PaymentQueryProvider.Request(command.providerRequestId(), facts.getMerchantReference(),
                facts.getProviderTransactionNo(), facts.getCreatedAt(), facts.getAmount().longValueExact(),
                clock.instant());
    }
    private PaymentProvider.VerifiedResult verified(PaymentAttemptRepository.ReconciliationFacts facts,
            PaymentQueryProvider.Result result) {
        return new PaymentProvider.VerifiedResult(facts.getMerchantReference(), facts.getAmount().longValueExact(),
                result.providerTransactionNo(), result.responseCode(), result.transactionStatus(),
                result.providerPaidAt(), result.evidenceHash());
    }
    private static BusinessConflictException mismatchConflict() {
        return new BusinessConflictException("PAYMENT_RECONCILIATION_RESULT_MISMATCH",
                "The VNPAY result does not match this payment attempt.");
    }
    private static View view(PaymentReconciliationCommand command, Context context, boolean replayed) {
        String current = command.currentAttemptStatus() == null ? context.facts().getStatus() : command.currentAttemptStatus();
        String previous = command.previousAttemptStatus() == null ? current : command.previousAttemptStatus();
        return new View(context.facts().getAttemptId(), context.orderId(),
                context.facts().getMerchantReference(), previous, current, outcome(current),
                "SUCCEEDED".equals(current) || "FAILED".equals(current), replayed);
    }
    private static String outcome(String state) {
        return switch (state) {
            case "SUCCEEDED" -> "RESOLVED_PAID";
            case "FAILED" -> "RESOLVED_FAILED";
            case "REVIEW_REQUIRED" -> "STILL_REVIEW_REQUIRED";
            default -> "STILL_UNRESOLVED";
        };
    }

    private record Context(PaymentAttemptRepository.ReconciliationFacts facts, Location location) {
        UUID orderId() { return facts.getOrderId(); }
        boolean reviewRequired() { return "REVIEW_REQUIRED".equals(facts.getStatus()); }
    }
    private record Work(UUID commandId, boolean dispatch, PaymentQueryProvider.Request request, View view) { }
    public record View(UUID attemptId, UUID orderId, String providerReference, String previousState,
            String currentState, String outcome, boolean resolved, boolean replayed) { }
}
