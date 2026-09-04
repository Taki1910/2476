package com.shoecommerce.platform.api;

public class BusinessConflictException extends RuntimeException {
    private final String code;
    private final java.util.UUID variantId;
    public BusinessConflictException(String message) { this("BUSINESS_CONFLICT", message); }
    public BusinessConflictException(String code, String message) { this(code, message, null); }
    public BusinessConflictException(String code, String message, java.util.UUID variantId) { super(message); this.code = code; this.variantId = variantId; }
    public String code() { return code; }
    public java.util.UUID variantId() { return variantId; }
}
