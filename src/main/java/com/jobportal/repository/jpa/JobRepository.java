package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.Job;
import com.jobportal.domain.enums.JobStatus;
import com.jobportal.domain.enums.VisaStatus;
import com.jobportal.domain.enums.WorkMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    @Override
    @EntityGraph(attributePaths = {"company"})
    org.springframework.data.domain.Page<Job> findAll(Specification<Job> spec, Pageable pageable);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByCompanyId(Long companyId, Pageable pageable);

    Page<Job> findByCompanyIdAndStatus(Long companyId, JobStatus status, Pageable pageable);

    Page<Job> findByCountryAndStatus(String country, JobStatus status, Pageable pageable);

    Page<Job> findByVisaStatus(VisaStatus visaStatus, Pageable pageable);

    @Query("SELECT j FROM Job j JOIN FETCH j.company WHERE j.id = :id")
    Optional<Job> findByIdWithCompany(@Param("id") Long id);

    @Query("SELECT j FROM Job j JOIN FETCH j.company " +
           "WHERE j.status = 'ACTIVE' ORDER BY j.createdAt DESC")
    Page<Job> findAllActiveWithCompany(Pageable pageable);

    boolean existsByExternalIdAndCompanyId(String externalId, Long companyId);

    Optional<Job> findByExternalIdAndCompanyId(String externalId, Long companyId);

    long countByStatus(JobStatus status);

    long countByVisaStatus(VisaStatus visaStatus);

    long countByCompanyId(Long companyId);

    long countByCompanyIdAndStatus(Long companyId, JobStatus status);

    @Query("SELECT j FROM Job j WHERE j.company.id = :companyId AND j.status = 'ACTIVE' " +
           "AND (j.scraperRunId IS NULL OR j.scraperRunId <> :runId)")
    List<Job> findActiveJobsNotSeenInRun(@Param("companyId") Long companyId, @Param("runId") String runId);

    long countByWorkMode(WorkMode workMode);

    @Query("SELECT COUNT(DISTINCT j.country) FROM Job j WHERE j.country IS NOT NULL AND j.country != ''")
    long countDistinctCountries();

    @Query("SELECT DISTINCT j.country FROM Job j WHERE j.country IS NOT NULL AND j.status = 'ACTIVE' ORDER BY j.country")
    List<String> findDistinctCountries();

    @Query("SELECT DISTINCT j.continent FROM Job j WHERE j.continent IS NOT NULL AND j.status = 'ACTIVE' ORDER BY j.continent")
    List<String> findDistinctContinents();

    @Query("SELECT j.country, COUNT(j) FROM Job j WHERE j.status = 'ACTIVE' GROUP BY j.country ORDER BY COUNT(j) DESC")
    List<Object[]> countByCountry();

    @Query("SELECT j.country, COUNT(j) FROM Job j WHERE j.status = 'ACTIVE' AND j.country IS NOT NULL GROUP BY j.country ORDER BY COUNT(j) DESC")
    Page<Object[]> countByCountryPaged(Pageable pageable);

    @Query("SELECT DISTINCT j.continent, j.country FROM Job j " +
           "WHERE j.continent IS NOT NULL AND j.continent <> '' " +
           "AND j.country IS NOT NULL AND j.country <> '' " +
           "AND j.status = 'ACTIVE' " +
           "ORDER BY j.continent, j.country")
    List<Object[]> findContinentCountryPairs();

    @Query(value = "SELECT c.name, COUNT(j.id) FROM jobs j " +
           "JOIN companies c ON j.company_id = c.id " +
           "WHERE j.created_at >= :since " +
           "GROUP BY c.name " +
           "ORDER BY COUNT(j.id) DESC",
           nativeQuery = true)
    List<Object[]> findRecentJobCountsByCompany(@Param("since") Instant since);
}
