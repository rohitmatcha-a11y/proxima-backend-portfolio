package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.enums.ScraperStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScraperConfigRepository extends JpaRepository<ScraperConfig, Long> {

    List<ScraperConfig> findByIsEnabledTrue();

    Optional<ScraperConfig> findByCompanyId(Long companyId);

    @Query("SELECT sc FROM ScraperConfig sc JOIN sc.company c WHERE c.slug = :slug")
    Optional<ScraperConfig> findByCompanySlug(@Param("slug") String slug);

    List<ScraperConfig> findByStatus(ScraperStatus status);
}
