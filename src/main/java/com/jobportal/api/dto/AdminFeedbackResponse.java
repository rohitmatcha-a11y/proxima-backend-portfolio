package com.jobportal.api.dto;

import com.jobportal.domain.enums.FeedbackStatus;
import com.jobportal.domain.enums.FeedbackType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AdminFeedbackResponse {
    Long id;
    FeedbackType type;
    FeedbackStatus status;
    String message;
    String submittedByName;
    String submittedByEmail;
    Instant submittedAt;
    Instant updatedAt;
}
