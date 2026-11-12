package com.jobportal.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.config.AuthProperties;
import com.jobportal.exception.ErrorCode;
import com.jobportal.exception.JobPortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import com.jobportal.util.SafeErrorSummary;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubOAuthService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String USER_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String GITHUB_API_VERSION = "2022-11-28";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isConfigured() {
        return StringUtils.hasText(authProperties.getGithubClientId())
                && StringUtils.hasText(authProperties.getGithubClientSecret());
    }

    public URI authorizationUri(String state) {
        requireConfiguration();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", authProperties.getGithubClientId())
                .queryParam("redirect_uri", authProperties.getGithubRedirectUri())
                .queryParam("scope", "read:user user:email")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    public GithubIdentity exchangeCode(String code) {
        requireConfiguration();
        try {
            String accessToken = exchangeAccessToken(code);
            JsonNode user = getJson(USER_URL, accessToken);
            String subject = user.path("id").asText();
            String email = user.path("email").asText(null);
            if (!StringUtils.hasText(email)) {
                email = primaryVerifiedEmail(accessToken).orElse(null);
            }

            if (!StringUtils.hasText(subject) || !StringUtils.hasText(email)) {
                throw githubSignInFailed();
            }

            String name = user.path("name").asText(null);
            if (!StringUtils.hasText(name)) {
                name = user.path("login").asText(null);
            }
            if (!StringUtils.hasText(name)) {
                name = email.substring(0, email.indexOf('@'));
            }
            return new GithubIdentity(subject, email, name.trim());
        } catch (IOException ex) {
            log.warn("GitHub sign-in response could not be processed: {}", SafeErrorSummary.from(ex));
            throw githubSignInFailed();
        }
    }

    private String exchangeAccessToken(String code) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody(code)))
                .build();
        JsonNode response = send(request);
        String accessToken = response.path("access_token").asText();
        if (!StringUtils.hasText(accessToken)) {
            throw githubSignInFailed();
        }
        return accessToken;
    }

    private Optional<String> primaryVerifiedEmail(String accessToken) throws IOException {
        JsonNode emails = getJson(USER_EMAILS_URL, accessToken);
        if (!emails.isArray()) return Optional.empty();
        return StreamSupport.stream(emails.spliterator(), false)
                .filter(email -> email.path("verified").asBoolean(false))
                .sorted(Comparator.comparing(email -> !email.path("primary").asBoolean(false)))
                .map(email -> email.path("email").asText(null))
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private JsonNode getJson(String url, String accessToken) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header(HttpHeaders.USER_AGENT, "Jobz")
                .GET()
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("GitHub OAuth request failed with status {}", response.statusCode());
                throw githubSignInFailed();
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw githubSignInFailed();
        }
    }

    private String formBody(String code) {
        return "client_id=" + encode(authProperties.getGithubClientId())
                + "&client_secret=" + encode(authProperties.getGithubClientSecret())
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(authProperties.getGithubRedirectUri());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireConfiguration() {
        if (!isConfigured()) {
            throw new JobPortalException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "GitHub sign-in is not configured for this environment", 503);
        }
    }

    private JobPortalException githubSignInFailed() {
        return new JobPortalException(ErrorCode.INVALID_CREDENTIALS,
                "GitHub sign-in could not be verified", 401);
    }
}
