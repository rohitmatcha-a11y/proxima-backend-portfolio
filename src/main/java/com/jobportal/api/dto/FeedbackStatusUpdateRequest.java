package com.jobportal.api.dto;

import com.jobportal.domain.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackStatusUpdateRequest {

    @NotNull(message = "Choose a feedback status")
    private FeedbackStatus status;
}
