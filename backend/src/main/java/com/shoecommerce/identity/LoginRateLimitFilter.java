package com.shoecommerce.identity;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

final class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginRateLimiter limiter;
    private final RestSecurityErrorWriter errorWriter;

    LoginRateLimitFilter(LoginRateLimiter limiter, RestSecurityErrorWriter errorWriter) {
        this.limiter = limiter;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v1/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var decision = limiter.acquire(request.getParameter("username"), request.getRemoteAddr());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED",
                    "Too many sign-in attempts. Please try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
