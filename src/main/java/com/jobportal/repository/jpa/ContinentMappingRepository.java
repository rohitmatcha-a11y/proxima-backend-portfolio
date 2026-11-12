package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.ContinentMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContinentMappingRepository extends JpaRepository<ContinentMapping, String> {
}
