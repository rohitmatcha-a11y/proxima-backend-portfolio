package com.jobportal.service.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.domain.enums.VisaStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.jobportal.exception.ExternalServiceException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.jobportal.util.SafeErrorSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisaDetectionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${app.visa.detection-enabled:true}")
    private boolean visaDetectionEnabled;

    @PostConstruct
    public void init() {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key NOT configured");
        } else {
            log.info("Anthropic API key configured");
        }
    }

    @Value("${external.anthropic.url}")
    private String anthropicUrl;

    @Value("${external.anthropic.version:2023-06-01}")
    private String anthropicVersion;

    @Value("${app.visa.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${app.visa.max-description-chars:3000}")
    private int maxDescriptionChars;

    public VisaDetectionResult detectVisa(Long jobId, String jobTitle,
                                          String companyName, String description) {
        if (!visaDetectionEnabled || description == null || description.isBlank()) {
            log.debug("Visa detection skipped for job {} — disabled or blank description", jobId);
            return VisaDetectionResult.notMentioned();
        }

        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key not configured — skipping visa detection for job {}", jobId);
            return VisaDetectionResult.notMentioned();
        }

        try {
            String truncated = description.length() > maxDescriptionChars
                    ? description.substring(0, maxDescriptionChars)
                    : description;

            String prompt = buildPrompt(jobTitle, companyName, truncated);
            String responseText = callAnthropic(prompt);
            return parseResult(responseText, jobId);

        } catch (Exception e) {
            log.warn("Claude API call failed ({}), falling back to keyword detection", SafeErrorSummary.from(e));
            return keywordBasedDetection(description);
        }
    }

    private String buildPrompt(String jobTitle, String companyName, String description) {
        return """
                Analyze this job description and determine the visa sponsorship status.

                Job Title: %s
                Company: %s

                Job Description:
                %s

                Instructions:
                - Look for explicit mentions of visa sponsorship, work authorization, \
                H1B, H-1B, work visa, immigration support, relocation support
                - Look for phrases like "we sponsor", "visa sponsorship available", \
                "will sponsor", "sponsorship provided"
                - Look for negative phrases like "no sponsorship", "must be authorized \
                to work", "citizens only", "no visa support"
                - If nothing is mentioned about visa, return NOT_MENTIONED

                Respond ONLY with a valid JSON object, no other text:
                {
                  "visaStatus": "SPONSORED" or "NOT_SPONSORED" or "NOT_MENTIONED",
                  "confidence": 0.0 to 1.0,
                  "reason": "brief explanation under 100 words",
                  "keywords": ["relevant", "keywords", "found"]
                }
                """.formatted(
                jobTitle != null ? jobTitle : "Unknown",
                companyName != null ? companyName : "Unknown",
                description
        );
    }

    private String callAnthropic(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", anthropicVersion);

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 300,
                "system", "You are an expert at analyzing job descriptions for visa sponsorship. Always respond with valid JSON only. Never include markdown or extra text.",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(anthropicUrl, request, String.class);
        } catch (RestClientException e) {
            throw ExternalServiceException.anthropicUnavailable(e);
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            if (response.getStatusCode().value() == 429) {
                throw ExternalServiceException.rateLimitExceeded("Anthropic");
            }
            throw ExternalServiceException.anthropicUnavailable(
                    new RuntimeException("Anthropic returned HTTP " + response.getStatusCode()));
        }

        JsonNode root = safeParseJson(response.getBody());
        if (root == null) return null;

        return root.path("content").path(0).path("text").asText(null);
    }

    private VisaDetectionResult parseResult(String text, Long jobId) {
        if (text == null || text.isBlank()) {
            log.warn("Empty response from Anthropic for job {}", jobId);
            return VisaDetectionResult.unknown();
        }

        try {
            String cleaned = text.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("```\\s*$", "").strip();
            }

            JsonNode node = objectMapper.readTree(cleaned);

            VisaStatus status;
            try {
                status = VisaStatus.valueOf(node.path("visaStatus").asText("NOT_MENTIONED"));
            } catch (IllegalArgumentException e) {
                status = VisaStatus.NOT_MENTIONED;
            }

            double confidence = node.path("confidence").asDouble(0.0);
            String reason = node.path("reason").asText("");

            List<String> keywords = new ArrayList<>();
            JsonNode kwNode = node.path("keywords");
            if (kwNode.isArray()) {
                kwNode.forEach(kw -> keywords.add(kw.asText()));
            }

            return VisaDetectionResult.builder()
                    .visaStatus(status)
                    .confidence(confidence)
                    .reason(reason)
                    .keywords(keywords)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Anthropic response as JSON for job {}: {}", jobId, SafeErrorSummary.from(e));
            return VisaDetectionResult.unknown();
        }
    }

    private VisaDetectionResult keywordBasedDetection(String description) {
        String lower = description.toLowerCase();

        List<String> sponsoredKeywords = List.of(
                "visa sponsorship", "h1b", "h-1b",
                "will sponsor", "we sponsor",
                "immigration support", "immigration assistance",
                "work authorization provided", "employment visa",
                "global mobility", "visa filing",
                "work permit sponsored", "visa support provided",
                "sponsor your visa", "sponsoring visas"
        );

        List<String> notSponsoredKeywords = List.of(
                "work without a sponsorship",
                "work without sponsorship",
                "authorised to work without",
                "authorized to work without",
                "no visa sponsorship",
                "not able to sponsor",
                "cannot sponsor",
                "unable to sponsor",
                "not provide visa",
                "does not sponsor",
                "sponsorship is not",
                "not sponsoring",
                "no immigration support",
                "us persons only",
                "citizens only"
        );

        List<String> foundSponsored = sponsoredKeywords.stream()
                .filter(lower::contains).toList();
        List<String> foundNotSponsored = notSponsoredKeywords.stream()
                .filter(lower::contains).toList();

        if (!foundSponsored.isEmpty() && foundNotSponsored.isEmpty()) {
            return VisaDetectionResult.builder()
                    .visaStatus(VisaStatus.SPONSORED)
                    .confidence(0.80)
                    .reason("Keyword analysis: found sponsorship indicators — " + foundSponsored)
                    .keywords(foundSponsored)
                    .build();
        } else if (!foundNotSponsored.isEmpty()) {
            return VisaDetectionResult.builder()
                    .visaStatus(VisaStatus.NOT_SPONSORED)
                    .confidence(0.80)
                    .reason("Keyword analysis: found no-sponsorship indicators — " + foundNotSponsored)
                    .keywords(foundNotSponsored)
                    .build();
        }
        return VisaDetectionResult.notMentioned();
    }

    private JsonNode safeParseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response body: {}", SafeErrorSummary.from(e));
            return null;
        }
    }
}
