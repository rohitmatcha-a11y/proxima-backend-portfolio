package com.jobportal.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeRequestedEvent {

    private String companySlug;
    private String triggeredBy;   // "SCHEDULER" or "MANUAL"
    private Instant requestedAt;
}
