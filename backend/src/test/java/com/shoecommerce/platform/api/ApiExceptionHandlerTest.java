package com.shoecommerce.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
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

}
