package com.jobportal.api.dto;

import com.jobportal.domain.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class JobTrackingStateResponse {
    Long jobId;
    boolean saved;
    boolean tracked;
    boolean applied;
    Instant trackedAt;
    Instant appliedAt;
    ApplicationStatus applicationStatus;
}
