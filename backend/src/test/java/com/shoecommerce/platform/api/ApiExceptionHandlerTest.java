package com.shoecommerce.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    @Test
    void addsTheStableCodeAndCorrelationId() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        MDC.put(CorrelationIdFilter.MDC_KEY, "request-123");
        try {
            var response = new ApiExceptionHandler().createResponseEntity(
                    problem,
                    new HttpHeaders(),
                    HttpStatus.BAD_REQUEST,
                    new ServletWebRequest(new MockHttpServletRequest()));

            assertThat(response.getBody()).isSameAs(problem);
            assertThat(problem.getProperties())
                    .containsEntry("code", "HTTP_400")
                    .containsEntry("correlationId", "request-123");
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @Test
    void mapsMultipartLimitThroughTheSpringExtensionPoint() {
        var response = new ApiExceptionHandler().handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5L * 1024 * 1024),
                new HttpHeaders(),
                HttpStatus.PAYLOAD_TOO_LARGE,
                new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((ProblemDetail) response.getBody()).extracting(ProblemDetail::getProperties)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "FIT_IMAGE_TOO_LARGE");
    }

    @Test
    void exposesStableFieldValidationIdentifiersWithoutRejectedValues() {
        var errors = new BeanPropertyBindingResult(new Object(), "request");
        errors.addError(new FieldError("request", "fulfillment.delivery.receiverPhone", "+84-secret",
                false, new String[] { "Pattern" }, null, "must match"));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(errors);

        var response = new ApiExceptionHandler().handleMethodArgumentNotValid(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(problem.getProperties().get("fieldErrors"))
                .isEqualTo(java.util.Map.of("fulfillment.delivery.receiverPhone", "INVALID"));
        assertThat(problem.toString()).doesNotContain("+84-secret").doesNotContain("must match");
    }

    @Test
    void mapsProviderUnavailabilityToSafeRetryableContract() {
        var response = new ApiExceptionHandler().handlePaymentProviderUnavailable(
                new com.shoecommerce.payment.PaymentProviderUnavailableException("private configuration detail"),
                new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(problem.getProperties()).containsEntry("code", "PAYMENT_PROVIDER_UNAVAILABLE");
        assertThat(problem.getDetail()).isEqualTo("Payment is temporarily unavailable. Please retry or choose another method.");
        assertThat(problem.toString()).doesNotContain("private configuration detail");
    }

    @Test
    void exposesConflictFieldIdentifiersWithoutPersistenceDetails() {
        var mutable = new java.util.HashMap<>(java.util.Map.of("sku", "ALREADY_EXISTS"));
        var exception = new BusinessConflictException("CATALOG_SKU_ALREADY_EXISTS",
                "A variant already uses this SKU.", null, mutable);
        mutable.put("barcode", "ALREADY_EXISTS");

        var response = new ApiExceptionHandler().handleConflict(exception,
                new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problem.getProperties())
                .containsEntry("code", "CATALOG_SKU_ALREADY_EXISTS")
                .containsEntry("fieldErrors", java.util.Map.of("sku", "ALREADY_EXISTS"));
        assertThat(exception.fieldErrors()).isUnmodifiable();
    }

}
