package com.jobportal.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TriggerResponse {
    private String slug;
    private String runId;
    private String status;
    private String message;
    private Instant triggeredAt;
}
