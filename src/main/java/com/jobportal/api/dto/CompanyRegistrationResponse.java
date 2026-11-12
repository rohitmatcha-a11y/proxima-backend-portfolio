package com.jobportal.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRegistrationResponse {
    private CompanyResponse company;
    private String scraperProvider;
    private String scraperClass;
    private String greenhouseBoardSlug;
    private boolean scraperEnabled;
    private String initialRunId;
}
