package com.jobportal.service.company;

import com.jobportal.client.wikipedia.WikipediaClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyDescriptionServiceTest {

    private final WikipediaClient wikipediaClient = mock(WikipediaClient.class);
    private final CompanyDescriptionService service = new CompanyDescriptionService(wikipediaClient);

    @Test
    void usesTheResolvedEncyclopediaDescriptionWhenAvailable() {
        when(wikipediaClient.fetchDescription("Figma_(software)"))
                .thenReturn("A live description from the source.");

        String description = service.resolve("Figma", "Figma_(software)", null);

        assertThat(description).isEqualTo("A live description from the source.");
        verify(wikipediaClient).fetchDescription("Figma_(software)");
    }

    @Test
    void usesGenericFallbackWhenNoDescriptionCanBeResolved() {
        when(wikipediaClient.fetchDescription("Example Co")).thenReturn(null);

        String description = service.resolve("Example Co", null, null);

        assertThat(description).isEqualTo("Explore open roles and career opportunities at Example Co.");
    }

    @Test
    void replacesShortCareerMarketingCopyWhenAReferenceDescriptionIsAvailable() {
        when(wikipediaClient.fetchDescription("Example Co"))
                .thenReturn("Example Co is a software company that builds reliable developer tools for teams around the world.");

        String description = service.resolve("Example Co", null, "Join us and help build the future.");

        assertThat(description).startsWith("Example Co is a software company");
        verify(wikipediaClient).fetchDescription("Example Co");
    }

    @Test
    void acceptsAFullCompanyDescriptionWithoutAnExtraLookup() {
        String description = "Example Co builds a cloud software platform that helps engineering teams collaborate, monitor their services, and ship dependable products to customers worldwide.";

        assertThat(service.resolve("Example Co", null, description)).isEqualTo(description);
    }
}
