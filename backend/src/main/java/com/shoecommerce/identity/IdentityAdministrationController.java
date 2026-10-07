package com.shoecommerce.identity;

import java.util.UUID;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.branch.ScopeAdministrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/admin/identity")
class IdentityAdministrationController {
    private final IdentityAdministrationService identities;
    private final ScopeAdministrationService scopes;
    private final StaffAccessService staffAccess;

    IdentityAdministrationController(IdentityAdministrationService identities, ScopeAdministrationService scopes, StaffAccessService staffAccess) {
        this.identities = identities; this.scopes = scopes; this.staffAccess = staffAccess;
    }

    @GetMapping("/staff")
    List<StaffAccessService.StaffAccessRecord> staff(@AuthenticationPrincipal SessionPrincipal actor) { return staffAccess.allStaff(actor); }

    @PostMapping("/accounts")
    UUID create(@AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody CreateAccount request) {
        return identities.createAccount(actor, request.login(), request.password(), request.role());
    }

    @PutMapping("/accounts/{accountId}/enabled")
    boolean enabled(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID accountId, @RequestBody StaffAccessController.Toggle request) {
        return identities.setAccountEnabled(actor, accountId, request.active());
    }

    @PutMapping("/accounts/{accountId}/permissions/{permission}")
    boolean permission(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID accountId,
            @PathVariable PermissionCode permission, @RequestBody StaffAccessController.Toggle request) {
        return identities.setDirectPermission(actor, accountId, permission, request.active());
    }

    @PutMapping("/accounts/{accountId}/assignments")
    boolean assignment(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID accountId,
            @Valid @RequestBody StaffAccessController.Assignment request) {
        return scopes.setAssignment(actor, accountId, request.branchId(), request.locationId(), request.active());
    }

    record CreateAccount(@NotBlank @Size(max=254) String login, @NotBlank @Size(min=12, max=72) String password, @NotNull RoleCode role) { }
}
