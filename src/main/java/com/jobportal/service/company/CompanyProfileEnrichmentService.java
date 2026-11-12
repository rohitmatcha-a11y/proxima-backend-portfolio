package com.jobportal.service.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.domain.enums.Industry;
import com.jobportal.util.SafeErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects publicly exposed company metadata from a careers page before the first scrape.
 * It understands ATS-provided profile data (including Ashby's organization theme) and then
 * supplements it from the company's own site and JSON-LD Organization markup when available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProfileEnrichmentService {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobzJobPortal/1.0)";

    private final ObjectMapper objectMapper;

    public CompanyProfile enrich(String companyName, String careersPageUrl) {
        CompanyProfile careersProfile = profileFromUrl(companyName, careersPageUrl);
        String publicWebsite = companyWebsite(careersProfile.website());
        CompanyProfile websiteProfile = isPublicCompanyWebsite(publicWebsite)
                ? profileFromUrl(companyName, publicWebsite)
                : CompanyProfile.empty();

        // A first-party favicon or square logo remains legible in the compact job-card icon.
        // ATS boards frequently expose a wide wordmark which is correct branding, but becomes
        // unreadable when it is constrained to the square card treatment.
        return new CompanyProfile(
                first(websiteProfile.website(), careersProfile.website()),
                first(websiteProfile.logoUrl(), careersProfile.logoUrl()),
                first(websiteProfile.description(), careersProfile.description()),
                first(websiteProfile.linkedinUrl(), careersProfile.linkedinUrl()),
                first(websiteProfile.industry(), careersProfile.industry()),
                first(websiteProfile.headquartersCity(), careersProfile.headquartersCity()),
                first(websiteProfile.headquartersCountry(), careersProfile.headquartersCountry()),
                first(websiteProfile.size(), careersProfile.size())
        );
    }

    CompanyProfile fromDocument(String companyName, Document document, String sourceUrl) {
        CompanyProfile standard = standardMetadata(companyName, document, sourceUrl);
        CompanyProfile jsonLd = organizationJsonLd(document, sourceUrl);
        CompanyProfile ashby = ashbyMetadata(document);
        return merge(ashby, merge(jsonLd, standard));
    }

    private CompanyProfile profileFromUrl(String companyName, String url) {
        try {
            Document document = fetch(url);
            return document == null ? CompanyProfile.empty() : fromDocument(companyName, document, url);
        } catch (Exception e) {
            log.debug("Could not enrich company profile: {}", SafeErrorSummary.from(e));
            return CompanyProfile.empty();
        }
    }

    private Document fetch(String url) throws Exception {
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .execute();
        return response.statusCode() >= 200 && response.statusCode() < 300 ? response.parse() : null;
    }

    private CompanyProfile ashbyMetadata(Document document) {
        for (Element script : document.select("script")) {
            String data = script.data();
            int marker = data.indexOf("window.__appData");
            if (marker < 0) continue;
            try {
                int assignment = data.indexOf('=', marker);
                int start = data.indexOf('{', assignment);
                int end = data.lastIndexOf("};");
                if (assignment < 0 || start < 0 || end < start) continue;

                JsonNode organization = objectMapper.readTree(data.substring(start, end + 1)).path("organization");
                JsonNode theme = organization.path("theme");
                return new CompanyProfile(
                        text(organization, "publicWebsite"),
                        first(text(theme, "logoSquareImageUrl"), text(theme, "logoWordmarkImageUrl")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            } catch (Exception e) {
                log.debug("Could not read Ashby organization metadata: {}", SafeErrorSummary.from(e));
            }
        }
        return CompanyProfile.empty();
    }

    private CompanyProfile standardMetadata(String companyName, Document document, String sourceUrl) {
        String canonical = absolute(sourceUrl, attribute(document.selectFirst("link[rel=canonical]"), "href"));
        String sourceWebsite = companyWebsite(sourceUrl);
        String website = first(companyWebsite(canonical), sourceWebsite);

        String logo = first(
                absolute(sourceUrl, attribute(document.selectFirst("link[rel=apple-touch-icon], link[rel~=(?i)icon]"), "href")),
                absolute(sourceUrl, attribute(document.selectFirst("meta[property=og:logo], meta[name=logo], meta[itemprop=logo]"), "content")),
                absolute(sourceUrl, attribute(document.selectFirst("link[rel=preload][as=image][href*=logo], link[rel=preload][as=image][href*=wordmark]"), "href")),
                absolute(sourceUrl, attribute(document.selectFirst("meta[property=og:image], meta[name=twitter:image]"), "content"))
        );

        String description = text(document.selectFirst("meta[name=description], meta[property=og:description]"), "content");
        if (!usefulDescription(description, companyName)) description = null;

        String linkedinUrl = document.select("a[href*='linkedin.com/company/']").stream()
                .map(link -> absolute(sourceUrl, link.attr("href")))
                .filter(value -> value != null && value.contains("linkedin.com/company/"))
                .findFirst()
                .orElse(null);

        return new CompanyProfile(website, logo, description, linkedinUrl,
                inferIndustry(description), null, null, null);
    }

    private CompanyProfile organizationJsonLd(Document document, String sourceUrl) {
        List<JsonNode> organizations = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                collectOrganizations(objectMapper.readTree(script.data()), organizations);
            } catch (Exception ignored) {
                // Careers pages frequently contain unrelated or malformed JSON-LD blocks.
            }
        }
        if (organizations.isEmpty()) return CompanyProfile.empty();

        JsonNode organization = organizations.getFirst();
        JsonNode address = organization.path("address");
        String country = address.isTextual() ? address.asText() : first(text(address, "addressCountry"), text(address.path("addressCountry"), "name"));
        JsonNode employeeCount = organization.path("numberOfEmployees");
        String size = employeeCount.isTextual()
                ? employeeCount.asText()
                : employeeRange(employeeCount);

        return new CompanyProfile(
                companyWebsite(text(organization, "url")),
                absolute(sourceUrl, logoValue(organization.path("logo"))),
                text(organization, "description"),
                linkedInFromSameAs(organization.path("sameAs")),
                inferIndustry(text(organization, "industry")),
                text(address, "addressLocality"),
                blank(country) ? null : country,
                blank(size) ? null : size
        );
    }

    private void collectOrganizations(JsonNode node, List<JsonNode> organizations) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectOrganizations(child, organizations));
            return;
        }
        if (!node.isObject()) return;
        if (organizationType(node.path("@type"))) organizations.add(node);
        node.elements().forEachRemaining(child -> collectOrganizations(child, organizations));
    }

    private boolean organizationType(JsonNode type) {
        if (type.isTextual()) return type.asText("").toLowerCase(Locale.ROOT).contains("organization");
        if (type.isArray()) {
            for (JsonNode value : type) if (organizationType(value)) return true;
        }
        return false;
    }

    private String employeeRange(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        String min = text(value, "minValue");
        String max = text(value, "maxValue");
        String exact = text(value, "value");
        if (!blank(min) && !blank(max)) return min + "-" + max;
        return first(exact, min, max);
    }

    private String logoValue(JsonNode logo) {
        if (logo == null || logo.isNull() || logo.isMissingNode()) return null;
        return logo.isTextual() ? logo.asText() : first(text(logo, "url"), text(logo, "contentUrl"));
    }

    private String linkedInFromSameAs(JsonNode sameAs) {
        if (sameAs == null || sameAs.isNull()) return null;
        if (sameAs.isTextual()) return sameAs.asText().contains("linkedin.com/company/") ? sameAs.asText() : null;
        if (sameAs.isArray()) {
            for (JsonNode value : sameAs) {
                String url = linkedInFromSameAs(value);
                if (url != null) return url;
            }
        }
        return null;
    }

    private CompanyProfile merge(CompanyProfile primary, CompanyProfile fallback) {
        return new CompanyProfile(
                first(primary.website(), fallback.website()),
                first(primary.logoUrl(), fallback.logoUrl()),
                first(primary.description(), fallback.description()),
                first(primary.linkedinUrl(), fallback.linkedinUrl()),
                first(primary.industry(), fallback.industry()),
                first(primary.headquartersCity(), fallback.headquartersCity()),
                first(primary.headquartersCountry(), fallback.headquartersCountry()),
                first(primary.size(), fallback.size())
        );
    }

    /** Maps public company copy to the closest supported industry without per-company rules. */
    public Industry inferIndustry(String value) {
        if (blank(value)) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "fintech", "payment processing", "payments platform", "banking platform", "money movement", "financial technology")) return Industry.FINTECH;
        if (containsAny(normalized, "financial services", "financial", "banking", "insurance", "investment", "asset management")) return Industry.FINANCE;
        if (containsAny(normalized, "healthcare", "health care", "medical", "biotech", "life sciences", "healthtech")) return Industry.HEALTHCARE;
        if (containsAny(normalized, "education", "edtech", "learning platform", "online learning")) return Industry.EDUCATION;
        if (containsAny(normalized, "ecommerce", "e-commerce", "commerce", "retail", "marketplace")) return Industry.ECOMMERCE;
        if (containsAny(normalized, "gaming", "video game", "game studio")) return Industry.GAMING;
        if (containsAny(normalized, "media", "streaming", "creator platform", "publishing")) return Industry.MEDIA;
        if (containsAny(normalized, "logistics", "supply chain", "freight", "delivery platform")) return Industry.LOGISTICS;
        if (containsAny(normalized, "real estate", "property technology", "proptech")) return Industry.REAL_ESTATE;
        if (containsAny(normalized, "energy", "climate technology", "clean energy")) return Industry.ENERGY;
        if (containsAny(normalized,
                "software", "technology", "saas", "artificial intelligence", "machine learning",
                "cloud", "observability", "monitoring", "cybersecurity", "security platform",
                "data platform", "developer", "infrastructure", "web platform", "design platform",
                "collaboration platform", "analytics platform")) return Industry.TECHNOLOGY;
        return null;
    }

    private boolean usefulDescription(String value, String companyName) {
        if (blank(value) || value.trim().length() < 100) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String company = companyName == null ? "" : companyName.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals(company)
                && !normalized.equals(company + " jobs")
                && !normalized.equals(company + " careers")
                && !normalized.equals("careers")
                && !normalized.contains("join our team")
                && !normalized.contains("join us")
                && !normalized.contains("we're hiring")
                && !normalized.contains("we are hiring")
                && !normalized.contains("work with us");
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    /**
     * A careers subdomain belongs to the company, but its root site is normally where canonical
     * profile data, social links and a square favicon live. Keep the first-party hostname while
     * discarding only well-known ATS hosts.
     */
    private String companyWebsite(String value) {
        if (!isPublicCompanyWebsite(value)) return null;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            for (String prefix : List.of("jobs.", "careers.", "work.")) {
                if (host.startsWith(prefix) && host.length() > prefix.length()) {
                    host = host.substring(prefix.length());
                    break;
                }
            }
            return uri.getScheme() + "://" + host + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isPublicCompanyWebsite(String value) {
        if (blank(value)) return false;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return !isAtsHost(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isAtsHost(String host) {
        return host.contains("ashbyhq.com")
                || host.contains("greenhouse.io")
                || host.contains("lever.co")
                || host.contains("myworkdayjobs.com")
                || host.contains("smartrecruiters.com")
                || host.contains("workable.com")
                || host.contains("bamboohr.com")
                || host.contains("jobvite.com")
                || host.contains("icims.com")
                || host.contains("applytojob.com")
                || host.contains("recruitee.com")
                || host.contains("teamtailor.com");
    }

    private String absolute(String baseUrl, String value) {
        if (blank(value)) return null;
        try {
            return URI.create(baseUrl).resolve(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String attribute(Element element, String attribute) {
        return element == null ? null : element.attr(attribute);
    }

    private String text(Element element, String attribute) {
        return element == null ? null : blank(element.attr(attribute)) ? null : element.attr(attribute).trim();
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? null : value;
    }

    private String first(String... values) {
        for (String value : values) if (!blank(value)) return value.trim();
        return null;
    }

    private <T> T first(T first, T fallback) {
        return first != null ? first : fallback;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record CompanyProfile(
            String website,
            String logoUrl,
            String description,
            String linkedinUrl,
            Industry industry,
            String headquartersCity,
            String headquartersCountry,
            String size
    ) {
        static CompanyProfile empty() {
            return new CompanyProfile(null, null, null, null, null, null, null, null);
        }
    }
}
