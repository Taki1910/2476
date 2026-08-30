package com.shoecommerce.identity;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.shoecommerce.branch.ScopeStore;

@Component
public class AuthorizationPolicy {

    private final UserAccountRepository accounts;
    private final AuthorityStore authorities;
    private final ScopeStore scopes;

    public AuthorizationPolicy(
            UserAccountRepository accounts,
            AuthorityStore authorities,
            ScopeStore scopes) {
        this.accounts = accounts;
        this.authorities = authorities;
        this.scopes = scopes;
    }

    public void requirePermission(SessionPrincipal actor, PermissionCode permission) {
        requireCurrent(actor);
        if (!authorities.hasPermission(actor.accountId(), permission)) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    public void requireBranchAccess(SessionPrincipal actor, UUID branchPublicId) {
        requireCurrent(actor);
        // Persisted assignments are authority; session-cached scope would survive reassignment.
        if (!scopes.hasBranchAccess(actor.accountId(), branchPublicId)) {
            throw new AccessDeniedException("Branch access denied");
        }
    }

    public void requireLocationAccess(SessionPrincipal actor, UUID locationPublicId) {
        requireCurrent(actor);
        if (!scopes.hasLocationAccess(actor.accountId(), locationPublicId)) {
            throw new AccessDeniedException("Location access denied");
        }
    }

    void requireCurrent(SessionPrincipal actor) {
        UserAccount account = accounts.findById(actor.accountId())
                .orElseThrow(() -> new AccessDeniedException("Account authority is unavailable"));
        if (account.status() != UserAccount.Status.ENABLED
                || account.authVersion() != actor.authVersion()) {
            throw new AccessDeniedException("Account authority is stale");
        }
    }
}
