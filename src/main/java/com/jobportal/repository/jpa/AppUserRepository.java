package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByGoogleSubject(String googleSubject);

    Optional<AppUser> findByGithubSubject(String githubSubject);

    boolean existsByEmailIgnoreCase(String email);
}
