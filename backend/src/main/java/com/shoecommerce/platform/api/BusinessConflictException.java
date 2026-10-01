package com.shoecommerce.platform.api;

public class BusinessConflictException extends RuntimeException {
    private final String code;
    private final java.util.UUID variantId;
    private final java.util.Map<String, String> fieldErrors;
    public BusinessConflictException(String message) { this("BUSINESS_CONFLICT", message); }
    public BusinessConflictException(String code, String message) { this(code, message, null); }
    public BusinessConflictException(String code, String message, java.util.UUID variantId) {
        this(code, message, variantId, java.util.Map.of());
    }
    public BusinessConflictException(String code, String message, java.util.UUID variantId,
            java.util.Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.variantId = variantId;
        this.fieldErrors = java.util.Map.copyOf(fieldErrors == null ? java.util.Map.of() : fieldErrors);
    }
    public String code() { return code; }
    public java.util.UUID variantId() { return variantId; }
    public java.util.Map<String, String> fieldErrors() { return fieldErrors; }
}
