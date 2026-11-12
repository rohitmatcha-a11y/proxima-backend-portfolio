package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.FeedbackSubmission;
import com.jobportal.domain.enums.FeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackSubmissionRepository extends JpaRepository<FeedbackSubmission, Long> {

    Page<FeedbackSubmission> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<FeedbackSubmission> findByStatusOrderByCreatedAtDesc(FeedbackStatus status, Pageable pageable);
}
