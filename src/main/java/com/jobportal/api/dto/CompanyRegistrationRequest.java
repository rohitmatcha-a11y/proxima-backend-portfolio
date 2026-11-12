package com.jobportal.api.dto;

import com.jobportal.domain.enums.Industry;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRegistrationRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String slug;
    private String website;
    private String logoUrl;
    private String description;
    private Industry industry;
    private String headquartersCountry;
    private String headquartersCity;
    private String size;
    private String linkedinUrl;

    @NotBlank(message = "Careers page URL is required")
    private String careersPageUrl;

    /** Optional legacy override. New registrations auto-detect the careers platform. */
    private String scraperProvider;

    /** Optional legacy Greenhouse override. It is no longer required in the admin UI. */
    private String greenhouseBoardSlug;

    @Builder.Default
    private boolean enableScraper = true;

    @Builder.Default
    private boolean triggerInitialScrape = true;
}
