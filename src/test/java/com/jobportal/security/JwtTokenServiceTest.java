package com.jobportal.security;

import com.jobportal.config.AuthProperties;
import com.jobportal.domain.entity.AppUser;
import com.jobportal.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void issuesAndReadsASignedTokenForTheCurrentUser() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("development-test-secret-with-at-least-thirty-two-characters");
        JwtTokenService tokenService = new JwtTokenService(properties);
        tokenService.initializeSigningKey();

        AppUser user = AppUser.builder()
                .email("member@example.com")
                .role(UserRole.USER)
                .build();
        user.setId(42L);

        JwtTokenService.IssuedToken token = tokenService.issue(user);

        assertThat(tokenService.getUserId(token.value())).contains(42L);
        assertThat(tokenService.getUserId(token.value() + "tampered")).isEmpty();
    }
}
