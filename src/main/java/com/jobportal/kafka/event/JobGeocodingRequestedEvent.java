package com.jobportal.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobGeocodingRequestedEvent {
    private Long jobId;
    private String locationText;
    private String title;
    private String companySlug;
}
