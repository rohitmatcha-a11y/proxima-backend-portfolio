package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.GeoLocationEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeoLocationEntryRepository extends JpaRepository<GeoLocationEntry, String> {
}
