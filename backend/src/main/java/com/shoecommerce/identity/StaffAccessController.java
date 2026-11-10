package com.shoecommerce.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/operations/staff-access")
class StaffAccessController {
    private final StaffAccessService access;

    StaffAccessController(StaffAccessService access) { this.access = access; }

    @GetMapping List<StaffAccessService.StaffAccessRecord> list(@AuthenticationPrincipal SessionPrincipal actor) { return access.manageableStaff(actor); }
    @GetMapping("/{accountId}") StaffAccessService.StaffAccessRecord detail(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID accountId) { return access.manageableStaff(actor, accountId); }
    @GetMapping("/locations") List<StaffAccessService.LocationView> locations(@AuthenticationPrincipal SessionPrincipal actor) { return access.manageableLocations(actor); }
    @GetMapping("/capabilities") List<StaffAccessService.CapabilityView> capabilities(@AuthenticationPrincipal SessionPrincipal actor) { return access.allowedCapabilities(actor); }

    @PutMapping("/{accountId}/capabilities/{permission}")
    boolean capability(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID accountId,
            @PathVariable PermissionCode permission, @Valid @RequestBody Toggle request) {
        return access.setCapability(actor, accountId, permission, request.active());
    }

    @PutMapping("/{accountId}/assignments")
    boolean assignment(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID accountId,
            @Valid @RequestBody Assignment request) {
        return access.setAssignment(actor, accountId, request.branchId(), request.locationId(), request.active());
    }

    record Toggle(boolean active) { }
    record Assignment(@NotNull UUID branchId, @NotNull UUID locationId, boolean active) { }
}
