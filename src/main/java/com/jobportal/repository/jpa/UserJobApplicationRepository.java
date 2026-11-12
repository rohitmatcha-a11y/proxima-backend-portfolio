package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.UserJobApplication;
import com.jobportal.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserJobApplicationRepository extends JpaRepository<UserJobApplication, Long> {

    @EntityGraph(attributePaths = {"job", "job.company"})
    Page<UserJobApplication> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.company"})
    Page<UserJobApplication> findByUserIdAndStatus(Long userId, ApplicationStatus status, Pageable pageable);

    List<UserJobApplication> findByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    Optional<UserJobApplication> findByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);
}
