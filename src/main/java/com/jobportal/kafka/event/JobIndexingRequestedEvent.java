package com.jobportal.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobIndexingRequestedEvent {

    private Long jobId;
    private String companySlug;
    private String action;  // "INDEX" or "DELETE"
}
