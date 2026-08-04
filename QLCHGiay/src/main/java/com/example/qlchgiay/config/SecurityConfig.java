package com.example.qlchgiay.config;

import com.example.qlchgiay.controller.SessionUserControllerAdvice;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import com.example.qlchgiay.service.WorkSessionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@Configuration
public class SecurityConfig {
    static final int RESET_REQUEST_ATTEMPTS = 3;
    static final int LOCK_AFTER_FAILED_ATTEMPTS = 5;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new LegacyAwarePasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(TaiKhoanRepo accountRepo) {
        return username -> accountRepo.findWithEmployeeByTenDangNhap(username.trim())
                .map(SecurityConfig::toUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại"));
    }

    @Bean
    UserDetailsPasswordService userDetailsPasswordService(TaiKhoanRepo accountRepo) {
        return (user, newPassword) -> {
            TaiKhoan account = accountRepo.findByTenDangNhap(user.getUsername());
            if (account == null) {
                throw new UsernameNotFoundException("Tài khoản không tồn tại");
            }
            account.setMatKhau(newPassword);
            accountRepo.save(account);
            return User.withUserDetails(user).password(newPassword).build();
        };
    }

    @Bean
    AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            UserDetailsPasswordService passwordUpdater,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsPasswordService(passwordUpdater);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            TaiKhoanRepo accountRepo,
            WorkSessionService workSessionService
    ) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/login", "/quen-mat-khau", "/error",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/admin/**", "/khuyenmai", "/khuyenmai/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            TaiKhoan account = accountRepo
                                    .findWithEmployeeByTenDangNhap(authentication.getName())
                                    .orElseThrow(() -> new UsernameNotFoundException(
                                            "Tài khoản không tồn tại"
                                    ));
                            clearFailedLogins(account, accountRepo);
                            HttpSession session = request.getSession(true);
                            session.setAttribute("user", account);
                            session.setAttribute(
                                    "userName",
                                    SessionUserControllerAdvice.displayName(account, null)
                            );
                            session.setAttribute(
                                    "userRole",
                                    SessionUserControllerAdvice.displayRole(account, null)
                            );
                            var notifications =
                                    workSessionService.handleSuccessfulLogin(account, session);
                            if (notifications.isEmpty()) {
                                session.removeAttribute(
                                        WorkSessionService.NOTIFICATIONS_ATTRIBUTE
                                );
                            } else {
                                session.setAttribute(
                                        WorkSessionService.NOTIFICATIONS_ATTRIBUTE,
                                        notifications
                                );
                            }
                            response.sendRedirect(request.getContextPath() + "/dashboard");
                        })
                        .failureHandler((request, response, exception) -> {
                            boolean locked = exception instanceof LockedException;
                            if (exception instanceof BadCredentialsException) {
                                locked = registerFailedLogin(
                                        accountRepo,
                                        request.getParameter("username")
                                );
                            }
                            response.sendRedirect(
                                    request.getContextPath()
                                            + (locked ? "/login?locked" : "/login?error")
                            );
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .addLogoutHandler((request, response, authentication) -> {
                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                workSessionService.finishSession(session);
                            }
                        })
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );
        return http.build();
    }

    static boolean registerFailedLogin(TaiKhoanRepo accountRepo, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        TaiKhoan account = accountRepo.findByTenDangNhap(username.trim());
        if (account == null || SessionUserControllerAdvice.isAdmin(account)) {
            return false;
        }
        if (Boolean.TRUE.equals(account.getTamKhoaDangNhap())) {
            return true;
        }

        int failures = account.getSoLanDangNhapSai() == null
                ? 0
                : account.getSoLanDangNhapSai();
        failures++;
        account.setSoLanDangNhapSai(failures);
        if (failures >= RESET_REQUEST_ATTEMPTS) {
            account.setYeuCauDatLaiMatKhau(true);
        }
        if (failures > LOCK_AFTER_FAILED_ATTEMPTS) {
            account.setTamKhoaDangNhap(true);
        }
        accountRepo.save(account);
        return Boolean.TRUE.equals(account.getTamKhoaDangNhap());
    }

    private static void clearFailedLogins(TaiKhoan account, TaiKhoanRepo accountRepo) {
        if ((account.getSoLanDangNhapSai() == null || account.getSoLanDangNhapSai() == 0)
                && !Boolean.TRUE.equals(account.getYeuCauDatLaiMatKhau())) {
            return;
        }
        account.setSoLanDangNhapSai(0);
        account.setYeuCauDatLaiMatKhau(false);
        accountRepo.save(account);
    }

    private static UserDetails toUserDetails(TaiKhoan account) {
        String role = SessionUserControllerAdvice.isAdmin(account)
                ? "ADMIN"
                : "EMPLOYEE";
        return User.withUsername(account.getTenDangNhap())
                .password(account.getMatKhau())
                .roles(role)
                .accountLocked(Boolean.TRUE.equals(account.getTamKhoaDangNhap()))
                .disabled(isInactive(account.getTrangThai()))
                .build();
    }

    private static boolean isInactive(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.trim().toLowerCase(Locale.ROOT);
        return value.contains("ngừng")
                || value.contains("ngung")
                || value.contains("khóa")
                || value.contains("khoa")
                || value.contains("inactive")
                || value.contains("disable");
    }

    static final class LegacyAwarePasswordEncoder implements PasswordEncoder {
        private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

        @Override
        public String encode(CharSequence rawPassword) {
            return bcrypt.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (rawPassword == null || encodedPassword == null) {
                return false;
            }
            if (encodedPassword.startsWith("$2")) {
                return bcrypt.matches(rawPassword, encodedPassword);
            }
            return MessageDigest.isEqual(
                    rawPassword.toString().getBytes(StandardCharsets.UTF_8),
                    encodedPassword.getBytes(StandardCharsets.UTF_8)
            );
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return encodedPassword == null || !encodedPassword.startsWith("$2");
        }
    }
}
