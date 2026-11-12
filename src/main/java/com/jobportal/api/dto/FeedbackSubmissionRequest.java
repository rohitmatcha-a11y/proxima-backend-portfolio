package com.jobportal.api.dto;

import com.jobportal.domain.enums.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackSubmissionRequest {

    @NotNull(message = "Choose a feedback category")
    private FeedbackType type;

    @NotBlank(message = "Please enter your feedback")
    @Size(min = 10, max = 2000, message = "Feedback must be between 10 and 2,000 characters")
    private String message;
}
