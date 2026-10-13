package com.shoecommerce.payment;

public final class PaymentReconciliationResultMismatchException extends RuntimeException {
    public PaymentReconciliationResultMismatchException(String safeMessage) { super(safeMessage); }
}
