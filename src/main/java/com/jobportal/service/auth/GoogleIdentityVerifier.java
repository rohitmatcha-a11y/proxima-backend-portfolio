package com.jobportal.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.jobportal.config.AuthProperties;
import com.jobportal.exception.ErrorCode;
import com.jobportal.exception.JobPortalException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.jobportal.util.SafeErrorSummary;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleIdentityVerifier {

    private final AuthProperties authProperties;
    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void initializeVerifier() {
        if (!StringUtils.hasText(authProperties.getGoogleClientId())) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID must be configured before starting the API");
        }
        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(authProperties.getGoogleClientId()))
                .build();
    }

    public GoogleIdentity verify(String credential) {
        try {
            GoogleIdToken token = verifier.verify(credential);
            if (token == null) {
                throw invalidGoogleCredential();
            }

            GoogleIdToken.Payload payload = token.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            if (!StringUtils.hasText(subject) || !StringUtils.hasText(email)
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw invalidGoogleCredential();
            }

            String name = payload.get("name") instanceof String value && StringUtils.hasText(value)
                    ? value.trim()
                    : email.substring(0, email.indexOf('@'));
            return new GoogleIdentity(subject, email, name);
        } catch (GeneralSecurityException | IOException ex) {
            log.warn("Google ID token verification failed: {}", SafeErrorSummary.from(ex));
            throw invalidGoogleCredential();
        }
    }

    private JobPortalException invalidGoogleCredential() {
        return new JobPortalException(ErrorCode.INVALID_CREDENTIALS,
                "Google sign-in could not be verified", 401);
    }
}
