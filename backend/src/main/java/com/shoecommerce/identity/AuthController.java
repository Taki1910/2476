package com.shoecommerce.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    CurrentAccountResponse me(@AuthenticationPrincipal SessionPrincipal principal) {
        return CurrentAccountResponse.from(principal);
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record CurrentAccountResponse(
            UUID accountId,
            String login,
            List<String> roles,
            List<String> permissions) {

        static CurrentAccountResponse from(SessionPrincipal principal) {
            return new CurrentAccountResponse(
                    principal.publicId(),
                    principal.getUsername(),
                    principal.roles().stream().sorted().toList(),
                    principal.permissions().stream().sorted().toList());
        }
    }
}
