package com.jobportal.client.wikipedia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.util.SafeErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class WikipediaClient {

    private final ObjectMapper objectMapper;

    @Value("${external.wikipedia.api-url:https://en.wikipedia.org/w/api.php}")
    private String wikipediaApiUrl;

    @Value("${external.wikipedia.base-url:https://en.wikipedia.org/api/rest_v1/page/summary}")
    private String wikipediaSummaryUrl;

    /**
     * Fetch a company description from Wikipedia. The REST summary endpoint is used first
     * because it is resilient to article-title redirects; the action API remains a fallback.
     * Truncates at the last complete sentence within 600 characters.
     *
     * @param slug Wikipedia article title e.g. "Stripe_(company)", "Anthropic"
     * @return description string or null if fetch fails or returns empty
     */
    public String fetchDescription(String slug) {
        if (slug == null || slug.isBlank()) return null;
        try {
            String encodedTitle = URLEncoder.encode(slug, StandardCharsets.UTF_8).replace("+", "%20");
            JsonNode summary = getJson(wikipediaSummaryUrl + "/" + encodedTitle);
            if (summary != null) {
                String description = summary.path("extract").asText("").trim();
                if (!description.isBlank()) return truncateAtSentence(description);
            }

            JsonNode action = getJson(wikipediaApiUrl + "?action=query&titles=" + encodedTitle
                    + "&prop=extracts&exintro=true&explaintext=true&format=json");
            if (action != null) {
                JsonNode pages = action.path("query").path("pages");
                if (!pages.isEmpty()) {
                    String extract = pages.elements().next().path("extract").asText("").trim();
                    if (!extract.isBlank()) return truncateAtSentence(extract);
                }
            }
        } catch (Exception e) {
            log.debug("Wikipedia fetch failed: {}", SafeErrorSummary.from(e));
        }
        return null;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "JobzJobPortal/1.0");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        if (connection.getResponseCode() != 200) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return objectMapper.readTree(reader);
        }
    }

    private String truncateAtSentence(String extract) {
        if (extract.length() <= 600) return extract;
        String truncated = extract.substring(0, 600);
        int lastPeriod = Math.max(truncated.lastIndexOf(". "), truncated.lastIndexOf("."));
        return lastPeriod > 0 ? extract.substring(0, lastPeriod + 1) : truncated + "...";
    }
}
