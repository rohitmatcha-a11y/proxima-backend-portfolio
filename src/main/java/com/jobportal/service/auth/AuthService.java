package com.jobportal.service.auth;

import com.jobportal.api.dto.AuthSessionResponse;
import com.jobportal.api.dto.AuthUserResponse;
import com.jobportal.config.AuthProperties;
import com.jobportal.domain.entity.AppUser;
import com.jobportal.domain.enums.UserRole;
import com.jobportal.exception.ErrorCode;
import com.jobportal.exception.JobPortalException;
import com.jobportal.repository.jpa.AppUserRepository;
import com.jobportal.security.AuthPrincipal;
import com.jobportal.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;

    @Transactional
    public AuthResult googleSignIn(GoogleIdentity identity) {
        String email = normalizeEmail(identity.email());
        AppUser user = appUserRepository.findByGoogleSubject(identity.subject())
                .orElseGet(() -> findOrCreateGoogleUser(identity, email));
        if (!user.isEnabled()) {
            throw new JobPortalException(ErrorCode.ACCOUNT_DISABLED, 403);
        }

        user.setName(identity.name());
        user.setLastLoginAt(Instant.now());
        grantBootstrapAdminRoleIfEligible(user, email);
        user = appUserRepository.save(user);
        return createAuthResult(user);
    }

    @Transactional
    public AuthResult githubSignIn(GithubIdentity identity) {
        String email = normalizeEmail(identity.email());
        AppUser user = appUserRepository.findByGithubSubject(identity.subject())
                .orElseGet(() -> findOrCreateGithubUser(identity, email));
        if (!user.isEnabled()) {
            throw new JobPortalException(ErrorCode.ACCOUNT_DISABLED, 403);
        }

        user.setName(identity.name());
        user.setLastLoginAt(Instant.now());
        grantBootstrapAdminRoleIfEligible(user, email);
        user = appUserRepository.save(user);
        return createAuthResult(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(AuthPrincipal principal) {
        AppUser user = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new JobPortalException(ErrorCode.AUTHENTICATION_REQUIRED, 401));
        if (!user.isEnabled()) {
            throw new JobPortalException(ErrorCode.ACCOUNT_DISABLED, 403);
        }
        return toUserResponse(user);
    }

    private AuthResult createAuthResult(AppUser user) {
        JwtTokenService.IssuedToken token = jwtTokenService.issue(user);
        return new AuthResult(
                token.value(),
                AuthSessionResponse.builder()
                        .user(toUserResponse(user))
                        .expiresAt(token.expiresAt())
                        .build()
        );
    }

    private AuthUserResponse toUserResponse(AppUser user) {
        return AuthUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AppUser findOrCreateGoogleUser(GoogleIdentity identity, String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (StringUtils.hasText(existing.getGoogleSubject())
                            && !existing.getGoogleSubject().equals(identity.subject())) {
                        throw new JobPortalException(ErrorCode.INVALID_CREDENTIALS,
                                "This email address is already linked to a different Google account", 401);
                    }
                    existing.setGoogleSubject(identity.subject());
                    return existing;
                })
                .orElseGet(() -> AppUser.builder()
                        .name(identity.name())
                        .email(email)
                        .googleSubject(identity.subject())
                        .role(UserRole.USER)
                        .enabled(true)
                        .build());
    }

    private AppUser findOrCreateGithubUser(GithubIdentity identity, String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (StringUtils.hasText(existing.getGithubSubject())
                            && !existing.getGithubSubject().equals(identity.subject())) {
                        throw new JobPortalException(ErrorCode.INVALID_CREDENTIALS,
                                "This email address is already linked to a different GitHub account", 401);
                    }
                    existing.setGithubSubject(identity.subject());
                    return existing;
                })
                .orElseGet(() -> AppUser.builder()
                        .name(identity.name())
                        .email(email)
                        .githubSubject(identity.subject())
                        .role(UserRole.USER)
                        .enabled(true)
                        .build());
    }

    private void grantBootstrapAdminRoleIfEligible(AppUser user, String email) {
        AuthProperties.BootstrapAdmin bootstrapAdmin = authProperties.getBootstrapAdmin();
        if (bootstrapAdmin.isEnabled() && StringUtils.hasText(bootstrapAdmin.getEmail())
                && email.equals(normalizeEmail(bootstrapAdmin.getEmail()))) {
            user.setRole(UserRole.SUPER_ADMIN);
        }
    }

    public record AuthResult(String token, AuthSessionResponse response) {
    }
}
