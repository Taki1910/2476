package com.shoecommerce.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
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
    void recognizesOnlySqlServerDeadlockAndLockTimeoutAcrossWrapperChains() {
        SQLException deadlock = new SQLException("private deadlock graph", "40001", 1205);
        SQLException outer = new SQLException("outer JDBC exception", "S0000", 0);
        outer.setNextException(new SQLException("private lock target", "S0002", 1222));

        assertThat(SqlServerContentionClassifier.isRetryable(
                new CannotAcquireLockException("translated", deadlock))).isTrue();
        assertThat(SqlServerContentionClassifier.isRetryable(
                new IllegalStateException("wrapped", outer))).isTrue();
    }

    @Test
    void doesNotPromoteBroadLockOrDatabaseFailures() {
        assertThat(SqlServerContentionClassifier.isRetryable(
                new SQLException("serialization", "40001", 0))).isFalse();
        assertThat(SqlServerContentionClassifier.isRetryable(
                new SQLException("duplicate key", "23000", 2601))).isFalse();
        assertThat(SqlServerContentionClassifier.isRetryable(
                new CannotAcquireLockException("broad lock failure"))).isFalse();
    }

    @Test
    void mapsExactSqlServerContentionToSafeSameKeyRetryContract() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "contention-request");
        try {
            for (int errorCode : new int[] { 1205, 1222 }) {
                SQLException sql = new SQLException("private table and index detail", "40001", errorCode);
                var response = new ApiExceptionHandler().handleUnexpected(
                        new CannotAcquireLockException("Hibernate SQL detail", sql),
                        new ServletWebRequest(new MockHttpServletRequest()));

                ProblemDetail problem = (ProblemDetail) response.getBody();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
                assertThat(problem.getProperties())
                        .containsEntry("code", "CONCURRENCY_RETRY_REQUIRED")
                        .containsEntry("retryable", true)
                        .containsEntry("reuseSameIdempotencyKey", true)
                        .containsEntry("correlationId", "contention-request");
                assertThat(problem.getDetail())
                        .isEqualTo("This request conflicted with another operation. Try the same action again.");
                assertThat(problem.toString())
                        .doesNotContain("private table")
                        .doesNotContain("Hibernate")
                        .doesNotContain("1205")
                        .doesNotContain("1222");
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }

        var unrelated = new ApiExceptionHandler().handleUnexpected(
                new CannotAcquireLockException("broad", new SQLException("duplicate", "23000", 2601)),
                new ServletWebRequest(new MockHttpServletRequest()));
        ProblemDetail problem = (ProblemDetail) unrelated.getBody();
        assertThat(unrelated.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem.getProperties())
                .containsEntry("code", "HTTP_500")
                .doesNotContainKeys("retryable", "reuseSameIdempotencyKey");
    }

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
        assertThat(problem.getDetail()).isEqualTo(
                "Payment provider is temporarily unavailable. Check the current payment status before trying again.");
        assertThat(problem.toString()).doesNotContain("private configuration detail");
    }

    @Test
    void mapsQueryDrMismatchWithoutProviderEvidence() {
        var response = new ApiExceptionHandler().handlePaymentReconciliationMismatch(
                new com.shoecommerce.payment.PaymentReconciliationResultMismatchException(
                        "private signed response and merchant detail"),
                new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problem.getProperties()).containsEntry("code", "PAYMENT_RECONCILIATION_RESULT_MISMATCH");
        assertThat(problem.getDetail()).isEqualTo("The VNPAY result could not be matched to this payment attempt.");
        assertThat(problem.toString()).doesNotContain("private signed response").doesNotContain("merchant detail");
    }

    @Test
    void mapsAssistantFailuresToSpecificSafeContracts() {
        for (var category : com.shoecommerce.merchandising.MerchandisingAssistant.ProviderFailureCategory.values()) {
            var exception = new com.shoecommerce.merchandising.MerchandisingAssistantUnavailableException(
                    category, new IllegalStateException("secret provider payload"));
            var response = new ApiExceptionHandler().handleMerchandisingAssistantUnavailable(
                    exception, new ServletWebRequest(new MockHttpServletRequest()));
            ProblemDetail problem = (ProblemDetail) response.getBody();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(problem.getProperties()).containsEntry("code", exception.code());
            assertThat(problem.getDetail()).isEqualTo(
                    "Trợ lý nội dung hiện không khả dụng. Bạn vẫn có thể chỉnh sửa thủ công.");
            assertThat(problem.toString()).doesNotContain("secret provider payload");
        }
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
