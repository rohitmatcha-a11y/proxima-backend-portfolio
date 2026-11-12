package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.Company;
import com.jobportal.domain.enums.Industry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    List<Company> findByIsActiveTrue();

    List<Company> findByIndustry(Industry industry);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);
}
