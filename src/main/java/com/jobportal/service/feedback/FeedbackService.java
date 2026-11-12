package com.jobportal.service.feedback;

import com.jobportal.api.dto.AdminFeedbackResponse;
import com.jobportal.api.dto.FeedbackStatusUpdateRequest;
import com.jobportal.api.dto.FeedbackSubmissionRequest;
import com.jobportal.api.dto.FeedbackSubmissionResponse;
import com.jobportal.api.dto.PagedResponse;
import com.jobportal.domain.entity.AppUser;
import com.jobportal.domain.entity.FeedbackSubmission;
import com.jobportal.domain.enums.FeedbackStatus;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.jpa.AppUserRepository;
import com.jobportal.repository.jpa.FeedbackSubmissionRepository;
import com.jobportal.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final int ADMIN_PAGE_SIZE = 20;

    private final FeedbackSubmissionRepository feedbackSubmissionRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public FeedbackSubmissionResponse submit(FeedbackSubmissionRequest request, AuthPrincipal principal) {
        AppUser user = principal == null ? null : appUserRepository.findById(principal.userId())
                .filter(AppUser::isEnabled)
                .orElse(null);

        FeedbackSubmission submission = feedbackSubmissionRepository.save(FeedbackSubmission.builder()
                .user(user)
                .type(request.getType())
                .message(request.getMessage().trim())
                .status(FeedbackStatus.NEW)
                .build());

        return FeedbackSubmissionResponse.builder()
                .id(submission.getId())
                .type(submission.getType())
                .status(submission.getStatus())
                .submittedAt(submission.getCreatedAt())
                .message(submission.getMessage())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminFeedbackResponse> getAdminFeedback(int page, FeedbackStatus status) {
        PageRequest pageable = PageRequest.of(page, ADMIN_PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<FeedbackSubmission> submissions = status == null
                ? feedbackSubmissionRepository.findAllByOrderByCreatedAtDesc(pageable)
                : feedbackSubmissionRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PagedResponse.of(submissions.map(this::toAdminResponse), Map.of());
    }

    @Transactional
    public AdminFeedbackResponse updateStatus(Long id, FeedbackStatusUpdateRequest request) {
        FeedbackSubmission submission = feedbackSubmissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.feedback(id));
        submission.setStatus(request.getStatus());
        return toAdminResponse(feedbackSubmissionRepository.save(submission));
    }

    private AdminFeedbackResponse toAdminResponse(FeedbackSubmission submission) {
        AppUser user = submission.getUser();
        return AdminFeedbackResponse.builder()
                .id(submission.getId())
                .type(submission.getType())
                .status(submission.getStatus())
                .message(submission.getMessage())
                .submittedByName(user == null ? "Anonymous" : user.getName())
                .submittedByEmail(user == null ? null : user.getEmail())
                .submittedAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }
}
