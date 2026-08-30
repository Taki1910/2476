package com.shoecommerce.identity;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CurrentAuthorityFilter extends OncePerRequestFilter {

    private final UserAccountRepository accounts;
    private final RestSecurityErrorWriter errorWriter;
    private final Clock clock;
    private final Duration absoluteTimeout;

    public CurrentAuthorityFilter(
            UserAccountRepository accounts,
            RestSecurityErrorWriter errorWriter,
            Clock clock,
            @Value("${security.session.absolute-timeout:PT8H}") Duration absoluteTimeout) {
        this.accounts = accounts;
        this.errorWriter = errorWriter;
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean current = accounts.findById(principal.accountId())
                .filter(account -> account.status() == UserAccount.Status.ENABLED)
                .filter(account -> account.authVersion() == principal.authVersion())
                .isPresent();
        Instant now = clock.instant();
        boolean expired = !now.isBefore(principal.authenticatedAt().plus(absoluteTimeout));
        if (current && !expired) {
            filterChain.doFilter(request, response);
            return;
        }

        // Previously authenticated sessions must not retain grants after authVersion or status changes.
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "SESSION_AUTHORITY_STALE",
                "Authentication is no longer valid.");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/csrf");
    }
}
