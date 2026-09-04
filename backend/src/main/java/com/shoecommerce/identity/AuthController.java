package com.shoecommerce.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityAdministrationService identities;

    public AuthController(IdentityAdministrationService identities) {
        this.identities = identities;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    CurrentAccountResponse me(@AuthenticationPrincipal SessionPrincipal principal) {
        return CurrentAccountResponse.from(principal);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredAccountResponse register(@Valid @RequestBody RegisterRequest request) {
        return identities.registerCustomer(request.login(), request.password());
    }

    record RegisterRequest(@NotBlank @Size(max = 254) String login, @NotBlank @Size(min = 12, max = 72) String password) { }
    public record RegisteredAccountResponse(UUID accountId, String login) { }

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
