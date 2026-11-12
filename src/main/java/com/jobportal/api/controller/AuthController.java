package com.jobportal.api.controller;

import com.jobportal.api.dto.AuthSessionResponse;
import com.jobportal.api.dto.AuthUserResponse;
import com.jobportal.api.dto.AuthProvidersResponse;
import com.jobportal.api.dto.GoogleSignInRequest;
import com.jobportal.config.AuthProperties;
import com.jobportal.security.AuthPrincipal;
import com.jobportal.security.JwtAuthenticationFilter;
import com.jobportal.service.auth.AuthService;
import com.jobportal.service.auth.GithubOAuthService;
import com.jobportal.service.auth.GoogleIdentityVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Social sign-in and session management")
@RequiredArgsConstructor
public class AuthController {

    private static final String GITHUB_STATE_COOKIE = "jobz_github_oauth_state";
    private static final String GITHUB_RETURN_TO_COOKIE = "jobz_github_oauth_return_to";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthService authService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final GithubOAuthService githubOAuthService;
    private final AuthProperties authProperties;

    @PostMapping("/google")
    @Operation(summary = "Sign in with a verified Google ID token")
    public ResponseEntity<AuthSessionResponse> signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) {
        AuthService.AuthResult result = authService.googleSignIn(googleIdentityVerifier.verify(request.getCredential()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(result.token()).toString())
                .body(result.response());
    }

    @GetMapping("/providers")
    @Operation(summary = "Get the social sign-in providers configured for this environment")
    public AuthProvidersResponse providers() {
        return AuthProvidersResponse.builder()
                .google(StringUtils.hasText(authProperties.getGoogleClientId()))
                .github(githubOAuthService.isConfigured())
                .build();
    }

    @GetMapping("/github")
    @Operation(summary = "Start GitHub sign-in")
    public ResponseEntity<Void> startGithubSignIn(@RequestParam(required = false) String returnTo) {
        String safeReturnTo = safeReturnTo(returnTo);
        String state = newOauthState();
        return redirect(githubOAuthService.authorizationUri(state).toString(),
                oauthCookie(GITHUB_STATE_COOKIE, state),
                oauthCookie(GITHUB_RETURN_TO_COOKIE, safeReturnTo));
    }

    @GetMapping("/github/callback")
    @Operation(summary = "Complete GitHub sign-in")
    public ResponseEntity<Void> completeGithubSignIn(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @CookieValue(name = GITHUB_STATE_COOKIE, required = false) String expectedState,
            @CookieValue(name = GITHUB_RETURN_TO_COOKIE, required = false) String returnTo) {
        String safeReturnTo = safeReturnTo(returnTo);
        if (!StringUtils.hasText(code) || !validState(expectedState, state)) {
            return githubFailure(safeReturnTo);
        }

        try {
            AuthService.AuthResult result = authService.githubSignIn(githubOAuthService.exchangeCode(code));
            return redirect(frontendUrl(safeReturnTo),
                    sessionCookie(result.token()),
                    clearedOAuthCookie(GITHUB_STATE_COOKIE),
                    clearedOAuthCookie(GITHUB_RETURN_TO_COOKIE));
        } catch (RuntimeException ex) {
            return githubFailure(safeReturnTo);
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current browser session")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in user")
    public ResponseEntity<AuthUserResponse> currentUser(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(authService.currentUser(principal));
    }

    private ResponseEntity<Void> githubFailure(String returnTo) {
        return redirect(loginUrl(returnTo, "github"),
                clearedOAuthCookie(GITHUB_STATE_COOKIE),
                clearedOAuthCookie(GITHUB_RETURN_TO_COOKIE));
    }

    private ResponseEntity<Void> redirect(String location, ResponseCookie... cookies) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(location));
        for (ResponseCookie cookie : cookies) {
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.build();
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(JwtAuthenticationFilter.SESSION_COOKIE, token)
                .httpOnly(true)
                .secure(authProperties.isSecureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(authProperties.getTokenTtlMs()))
                .build();
    }

    private ResponseCookie clearedSessionCookie() {
        return ResponseCookie.from(JwtAuthenticationFilter.SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(authProperties.isSecureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie oauthCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.isSecureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(OAUTH_STATE_TTL)
                .build();
    }

    private ResponseCookie clearedOAuthCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(authProperties.isSecureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String newOauthState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean validState(String expected, String received) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(received)) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    private String safeReturnTo(String returnTo) {
        return StringUtils.hasText(returnTo) && returnTo.startsWith("/") && !returnTo.startsWith("//")
                ? returnTo
                : "/";
    }

    private String frontendUrl(String returnTo) {
        return java.net.URI.create(authProperties.getFrontendUrl()).resolve(returnTo).toString();
    }

    private String loginUrl(String returnTo, String oauthError) {
        return UriComponentsBuilder.fromUriString(authProperties.getFrontendUrl())
                .path("/login")
                .queryParam("returnTo", returnTo)
                .queryParam("oauthError", oauthError)
                .build()
                .encode()
                .toUriString();
    }
}
