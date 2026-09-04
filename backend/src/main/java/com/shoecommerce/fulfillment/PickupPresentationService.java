package com.shoecommerce.fulfillment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.payment.VoidService;

@Service
public class PickupPresentationService {
    private final PickupFulfillmentRepository fulfillments;
    private final VoidService voids;

    PickupPresentationService(PickupFulfillmentRepository fulfillments, VoidService voids) {
        this.fulfillments = fulfillments; this.voids = voids;
    }

    @Transactional(readOnly = true)
    public Presentation forOrder(CustomerOrder order) {
        PickupFulfillment fulfillment = fulfillments.findByOrder(order).orElse(null);
        VoidService.VoidView financial = voids.findByOrder(order.paymentFacts().orderId());
        String fulfillmentStatus = fulfillment == null ? null : fulfillment.status();
        String fulfillmentType = fulfillment == null ? null : fulfillment.type();
        String voidStatus = financial == null ? null : financial.status();
        String customerStatus;
        if (fulfillment != null && fulfillment.handedOver()) customerStatus = "PICKED_UP";
        else if (fulfillment != null && fulfillment.delivered()) customerStatus = "DELIVERED";
        else if (fulfillment != null && fulfillment.dispatched()) customerStatus = "OUT_FOR_DELIVERY";
        else if (fulfillment != null && fulfillment.cancelled()) {
            customerStatus = switch (voidStatus == null ? "PROCESSING" : voidStatus) {
                case "SUCCEEDED" -> "CANCELLED_PAYMENT_REVERSED";
                case "FAILED_RETRYABLE" -> "CANCELLED_REVERSAL_FAILED";
                case "REVIEW_REQUIRED" -> "CANCELLED_REVERSAL_REVIEW";
                default -> "CANCELLATION_PROCESSING";
            };
        } else if (fulfillment != null && fulfillment.prepared()) {
            customerStatus = fulfillment.fulfillmentType() == PickupFulfillment.Type.PICKUP
                    ? "READY_FOR_PICKUP" : "READY_FOR_DISPATCH";
        }
        else if (order.paymentFacts().paid()) customerStatus = "PAID_WAITING_PREPARATION";
        else customerStatus = order.paymentStatus();
        boolean cancellable = order.paymentFacts().paid()
                && (fulfillment == null || (!fulfillment.handedOver() && !fulfillment.dispatched()));
        return new Presentation(customerStatus, fulfillmentType, fulfillmentStatus, voidStatus, cancellable,
                fulfillment == null ? null : fulfillment.pickingStartedAt(),
                fulfillment == null ? null : fulfillment.preparedAt(),
                fulfillment == null ? null : fulfillment.handedOverAt(),
                fulfillment == null ? null : fulfillment.dispatchedAt(),
                fulfillment == null ? null : fulfillment.deliveredAt(),
                fulfillment == null ? null : fulfillment.cancelledAt(),
                fulfillment == null ? null : fulfillment.receiverName(),
                fulfillment == null ? null : fulfillment.receiverPhone(),
                fulfillment == null ? null : fulfillment.deliveryAddress(),
                fulfillment == null ? null : fulfillment.deliveryNote(),
                fulfillment == null ? 0 : fulfillment.deliveryFeeAmount());
    }

    public record Presentation(String customerStatus, String fulfillmentType, String fulfillmentStatus,
            String financialVoidStatus, boolean cancellationEligible, java.time.Instant acceptedAt,
            java.time.Instant readyAt, java.time.Instant handedOverAt, java.time.Instant dispatchedAt,
            java.time.Instant deliveredAt, java.time.Instant fulfillmentCancelledAt, String receiverName,
            String receiverPhone, String deliveryAddress, String deliveryNote, long deliveryFeeAmount) { }
}
