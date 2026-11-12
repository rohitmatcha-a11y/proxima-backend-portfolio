package com.jobportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String jwtSecret;
    private String googleClientId;
    private String githubClientId;
    private String githubClientSecret;
    private String githubRedirectUri = "http://localhost:8080/api/v1/auth/github/callback";
    private String frontendUrl = "http://localhost:3000";
    private String issuer = "job-portal";
    private long tokenTtlMs = 28_800_000L;
    private boolean secureCookies;
    private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    @Data
    public static class BootstrapAdmin {
        private boolean enabled;
        private String email;
    }
}
