package com.jobportal.api.controller;

import com.jobportal.api.dto.AdminFeedbackResponse;
import com.jobportal.api.dto.FeedbackStatusUpdateRequest;
import com.jobportal.api.dto.FeedbackSubmissionRequest;
import com.jobportal.api.dto.FeedbackSubmissionResponse;
import com.jobportal.api.dto.PagedResponse;
import com.jobportal.domain.enums.FeedbackStatus;
import com.jobportal.security.AuthPrincipal;
import com.jobportal.service.feedback.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@Validated
@Tag(name = "Feedback", description = "Public suggestions and feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit feedback anonymously or as the signed-in user")
    public FeedbackSubmissionResponse submit(@Valid @RequestBody FeedbackSubmissionRequest request,
                                             @AuthenticationPrincipal AuthPrincipal principal) {
        return feedbackService.submit(request, principal);
    }

    @GetMapping("/admin")
    @Operation(summary = "List feedback submissions for administrators")
    public PagedResponse<AdminFeedbackResponse> adminFeedback(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false) FeedbackStatus status) {
        return feedbackService.getAdminFeedback(page, status);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update feedback review status")
    public AdminFeedbackResponse updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody FeedbackStatusUpdateRequest request) {
        return feedbackService.updateStatus(id, request);
    }
}
