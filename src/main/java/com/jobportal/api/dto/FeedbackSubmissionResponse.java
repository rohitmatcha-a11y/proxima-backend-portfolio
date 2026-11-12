package com.jobportal.api.dto;

import com.jobportal.domain.enums.FeedbackStatus;
import com.jobportal.domain.enums.FeedbackType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class FeedbackSubmissionResponse {
    Long id;
    FeedbackType type;
    FeedbackStatus status;
    Instant submittedAt;
    String message;
}
