package com.jobportal.service.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves common ATS board identifiers from public careers URLs and embeds. */
public final class CareerSiteProviderDetector {

    private static final Pattern GREENHOUSE_API = Pattern.compile(
            "boards-api\\.greenhouse\\.io/v1/boards/([^/?#]+)/jobs?", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREENHOUSE_BOARD = Pattern.compile(
            "(?:boards|job-boards)\\.greenhouse\\.io/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREENHOUSE_EMBED = Pattern.compile(
            "[?&]for=([^&#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVER_API = Pattern.compile(
            "api\\.lever\\.co/v0/postings/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private CareerSiteProviderDetector() {}

    public static Detection detect(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return new Detection(CareerSiteProvider.STRUCTURED_DATA, null);
        }

        String url = rawUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        String board = greenhouseBoard(url);
        if (board != null) return new Detection(CareerSiteProvider.GREENHOUSE, board);

        if (lower.contains("lever.co")) {
            return new Detection(CareerSiteProvider.LEVER, firstPathSegment(url, LEVER_API));
        }
        if (lower.contains("ashbyhq.com")) {
            return new Detection(CareerSiteProvider.ASHBY, firstPathSegment(url, null));
        }
        return new Detection(CareerSiteProvider.STRUCTURED_DATA, null);
    }

    /** Finds an ATS URL when a company-owned careers page embeds or links to one. */
    public static Optional<Detection> detectEmbedded(Document document) {
        for (Element element : document.select("iframe[src], a[href], script[src], link[href]")) {
            String url = element.hasAttr("src") ? element.absUrl("src") : element.absUrl("href");
            Detection detection = detect(url);
            if (detection.provider() != CareerSiteProvider.STRUCTURED_DATA && detection.board() != null) {
                return Optional.of(detection);
            }
        }
        return Optional.empty();
    }

    private static String greenhouseBoard(String url) {
        for (Pattern pattern : new Pattern[]{GREENHOUSE_API, GREENHOUSE_BOARD, GREENHOUSE_EMBED}) {
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) return sanitize(matcher.group(1));
        }
        return null;
    }

    private static String firstPathSegment(String rawUrl, Pattern apiPattern) {
        if (apiPattern != null) {
            Matcher matcher = apiPattern.matcher(rawUrl);
            if (matcher.find()) return sanitize(matcher.group(1));
        }
        try {
            String path = URI.create(rawUrl).getPath();
            if (path == null) return null;
            for (String part : path.split("/")) {
                if (!part.isBlank()) return sanitize(part);
            }
        } catch (IllegalArgumentException ignored) {
            // The structured-data fallback gives the scrape a chance to succeed.
        }
        return null;
    }

    private static String sanitize(String value) {
        if (value == null) return null;
        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8)
                .trim()
                .replaceAll("[^A-Za-z0-9_-]", "");
        return decoded.isBlank() ? null : decoded;
    }

    public record Detection(CareerSiteProvider provider, String board) {}
}
