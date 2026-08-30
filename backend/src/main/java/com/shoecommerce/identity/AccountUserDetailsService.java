package com.shoecommerce.identity;

import java.time.Clock;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;
    private final AuthorityStore authorities;
    private final Clock clock;

    public AccountUserDetailsService(
            UserAccountRepository accounts,
            AuthorityStore authorities,
            Clock clock) {
        this.accounts = accounts;
        this.authorities = authorities;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = accounts.findByLoginNormalized(UserAccount.normalizeLogin(username))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new SessionPrincipal(
                account,
                account.passwordHash(),
                clock.instant(),
                authorities.roleCodes(account.id()),
                authorities.permissionCodes(account.id()));
    }
}
