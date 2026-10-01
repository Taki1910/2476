package com.shoecommerce.platform.api;

public class InvalidRequestException extends RuntimeException {
    private final String code;
    private final java.util.Map<String, String> fieldErrors;
    public InvalidRequestException(String code, String message) { this(code, message, java.util.Map.of()); }
    public InvalidRequestException(String code, String message, String field, String fieldCode) {
        this(code, message, java.util.Map.of(field, fieldCode));
    }
    public InvalidRequestException(String code, String message, java.util.Map<String, String> fieldErrors) {
        super(message); this.code = code; this.fieldErrors = java.util.Map.copyOf(fieldErrors);
    }
    public String code() { return code; }
    public java.util.Map<String, String> fieldErrors() { return fieldErrors; }
}
