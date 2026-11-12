package com.jobportal.service.company;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyProfileEnrichmentServiceTest {

    private final CompanyProfileEnrichmentService service = new CompanyProfileEnrichmentService(new ObjectMapper());

    @Test
    void readsPublicWebsiteAndWordmarkFromAshbyOrganizationData() {
        var document = Jsoup.parse("""
                <html><head><script>
                window.__appData = {
                  "organization": {
                    "publicWebsite": "https://linear.app/",
                    "theme": {
                      "logoSquareImageUrl": null,
                      "logoWordmarkImageUrl": "https://cdn.example.com/linear-wordmark.png"
                    }
                  }
                };
                </script></head></html>
                """, "https://jobs.ashbyhq.com/linear");

        var profile = service.fromDocument("Linear", document, "https://jobs.ashbyhq.com/linear");

        assertThat(profile.website()).isEqualTo("https://linear.app/");
        assertThat(profile.logoUrl()).isEqualTo("https://cdn.example.com/linear-wordmark.png");
    }

    @Test
    void readsOrganizationDetailsFromJsonLdOnACompanySite() {
        var document = Jsoup.parse("""
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Organization",
                  "url": "https://example.com",
                  "logo": "https://example.com/logo.png",
                  "description": "A technology company building useful software products.",
                  "industry": "Technology",
                  "sameAs": ["https://www.linkedin.com/company/example"],
                  "address": {"addressLocality": "Bengaluru", "addressCountry": "India"},
                  "numberOfEmployees": {"minValue": 51, "maxValue": 200}
                }
                </script>
                """, "https://example.com/careers");

        var profile = service.fromDocument("Example", document, "https://example.com/careers");

        assertThat(profile.website()).isEqualTo("https://example.com");
        assertThat(profile.logoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(profile.linkedinUrl()).isEqualTo("https://www.linkedin.com/company/example");
        assertThat(profile.headquartersCity()).isEqualTo("Bengaluru");
        assertThat(profile.headquartersCountry()).isEqualTo("India");
        assertThat(profile.size()).isEqualTo("51-200");
    }

    @Test
    void prefersACompanyFaviconOverAWideSocialPreviewForCardLogos() {
        var document = Jsoup.parse("""
                <html><head>
                  <link rel="icon" href="/favicon.ico">
                  <meta property="og:image" content="https://example.com/social-preview.png">
                </head></html>
                """, "https://example.com");

        var profile = service.fromDocument("Example", document, "https://example.com");

        assertThat(profile.logoUrl()).isEqualTo("https://example.com/favicon.ico");
    }

    @Test
    void normalizesACareersSubdomainAndClassifiesTechnologyProfiles() {
        var document = Jsoup.parse("""
                <html><head>
                  <meta name="description" content="A cloud observability and monitoring platform that helps engineering teams understand and improve the reliability of their applications.">
                </head></html>
                """, "https://jobs.example.com/careers");

        var profile = service.fromDocument("Example", document, "https://jobs.example.com/careers");

        assertThat(profile.website()).isEqualTo("https://example.com");
        assertThat(profile.industry()).isEqualTo(com.jobportal.domain.enums.Industry.TECHNOLOGY);
    }
}
