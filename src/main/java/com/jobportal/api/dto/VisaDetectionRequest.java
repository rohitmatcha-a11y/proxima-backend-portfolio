package com.jobportal.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisaDetectionRequest {
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String description;
}
