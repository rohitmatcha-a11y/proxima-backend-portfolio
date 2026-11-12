package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.UserSavedJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserSavedJobRepository extends JpaRepository<UserSavedJob, Long> {

    @EntityGraph(attributePaths = {"job", "job.company"})
    Page<UserSavedJob> findByUserId(Long userId, Pageable pageable);

    List<UserSavedJob> findByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    Optional<UserSavedJob> findByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    long countByUserId(Long userId);
}
