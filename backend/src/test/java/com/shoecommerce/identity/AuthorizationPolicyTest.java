package com.shoecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.shoecommerce.branch.ScopeStore;

class AuthorizationPolicyTest {

    @Test
    void permissionQueryUsesTheAuthoritativeStore() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorityStore authorities = mock(AuthorityStore.class);
        SessionPrincipal actor = currentActor(accounts);
        when(authorities.hasPermission(7L, PermissionCode.INVENTORY_VIEW)).thenReturn(true);

        boolean granted = new AuthorizationPolicy(accounts, authorities, mock(ScopeStore.class))
                .hasPermission(actor, PermissionCode.INVENTORY_VIEW);

        assertThat(granted).isTrue();
        verify(authorities).hasPermission(7L, PermissionCode.INVENTORY_VIEW);
    }

    @Test
    void permissionQueryDoesNotInferPrivilegeFromTheSession() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorityStore authorities = mock(AuthorityStore.class);
        SessionPrincipal actor = currentActor(accounts);
        when(actor.permissions()).thenReturn(java.util.Set.of(PermissionCode.INVENTORY_VIEW.name()));

        assertThat(new AuthorizationPolicy(accounts, authorities, mock(ScopeStore.class))
                .hasPermission(actor, PermissionCode.INVENTORY_VIEW)).isFalse();
    }

    @Test
    void permissionQueryRejectsStaleAuthorityBeforeReadingGrants() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorityStore authorities = mock(AuthorityStore.class);
        SessionPrincipal actor = mock(SessionPrincipal.class);
        UserAccount account = mock(UserAccount.class);
        when(actor.accountId()).thenReturn(7L);
        when(actor.authVersion()).thenReturn(2L);
        when(account.status()).thenReturn(UserAccount.Status.ENABLED);
        when(account.authVersion()).thenReturn(3L);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> new AuthorizationPolicy(accounts, authorities, mock(ScopeStore.class))
                .hasPermission(actor, PermissionCode.INVENTORY_VIEW))
                .isInstanceOf(AccessDeniedException.class);
        verify(authorities, never()).hasPermission(7L, PermissionCode.INVENTORY_VIEW);
    }

    private static SessionPrincipal currentActor(UserAccountRepository accounts) {
        SessionPrincipal actor = mock(SessionPrincipal.class);
        UserAccount account = mock(UserAccount.class);
        when(actor.accountId()).thenReturn(7L);
        when(actor.authVersion()).thenReturn(2L);
        when(account.status()).thenReturn(UserAccount.Status.ENABLED);
        when(account.authVersion()).thenReturn(2L);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        return actor;
    }
}
