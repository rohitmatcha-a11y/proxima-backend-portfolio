package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.ScraperRun;
import com.jobportal.domain.enums.ScraperRunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScraperRunRepository extends JpaRepository<ScraperRun, Long> {

    Optional<ScraperRun> findByRunId(String runId);

    List<ScraperRun> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    long countByCompanyIdAndStatus(Long companyId, ScraperRunStatus status);

    Optional<ScraperRun> findTopByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<ScraperRun> findTopByStatusOrderByCompletedAtDesc(ScraperRunStatus status);

    List<ScraperRun> findByStatus(ScraperRunStatus status);
    long countByStatus(ScraperRunStatus status);

    List<ScraperRun> findByStatusAndStartedAtBefore(ScraperRunStatus status, Instant cutoff);

    @Query("SELECT r FROM ScraperRun r WHERE r.company.slug = :slug ORDER BY r.createdAt DESC")
    List<ScraperRun> findByCompanySlugOrderByCreatedAtDesc(@Param("slug") String slug, Pageable pageable);

    @Query("SELECT r FROM ScraperRun r WHERE r.company.slug = :slug ORDER BY r.createdAt DESC")
    List<ScraperRun> findTopByCompanySlugOrderByCreatedAtDesc(@Param("slug") String slug, Pageable pageable);

    @Query("SELECT r FROM ScraperRun r WHERE r.company.slug = :slug AND r.status = :status")
    List<ScraperRun> findByCompanySlugAndStatus(@Param("slug") String slug,
                                                 @Param("status") ScraperRunStatus status);

    @Query("SELECT r FROM ScraperRun r JOIN FETCH r.company " +
           "WHERE r.status = 'RUNNING' AND r.startedAt < :cutoff")
    List<ScraperRun> findStuckRunsWithCompany(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(r) FROM ScraperRun r WHERE r.company.id = :companyId " +
           "AND r.status = 'FAILED' AND r.startedAt >= :since")
    long countRecentFailures(@Param("companyId") Long companyId, @Param("since") Instant since);
}
