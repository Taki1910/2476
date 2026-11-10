package com.shoecommerce.payment;

public final class PaymentProviderUnavailableException extends RuntimeException {
    public PaymentProviderUnavailableException(String internalReason) {
        super("Payment provider is unavailable");
    }
}
