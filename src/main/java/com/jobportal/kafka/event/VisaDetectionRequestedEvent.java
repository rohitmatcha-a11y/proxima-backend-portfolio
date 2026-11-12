package com.jobportal.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisaDetectionRequestedEvent {

    private Long jobId;
    private String jobTitle;
    private String companySlug;
    private String jobDescription;
}
