package com.shoecommerce.identity;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OwnershipPolicy {

    private final AuthorizationPolicy authorization;

    public OwnershipPolicy(AuthorizationPolicy authorization) {
        this.authorization = authorization;
    }

    public void requireOwnership(SessionPrincipal actor, UUID ownerAccountPublicId) {
        authorization.requireCurrent(actor);
        if (!actor.publicId().equals(ownerAccountPublicId)) {
            throw new AccessDeniedException("Resource ownership denied");
        }
    }
}
