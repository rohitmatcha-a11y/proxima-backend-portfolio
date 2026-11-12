package com.jobportal.service.profile;

import com.jobportal.api.dto.AuthUserResponse;
import com.jobportal.api.dto.JobTrackingStateResponse;
import com.jobportal.api.dto.PagedResponse;
import com.jobportal.api.dto.ProfileJobResponse;
import com.jobportal.api.dto.UserProfileResponse;
import com.jobportal.domain.entity.AppUser;
import com.jobportal.domain.entity.Job;
import com.jobportal.domain.entity.UserJobApplication;
import com.jobportal.domain.entity.UserSavedJob;
import com.jobportal.domain.enums.ApplicationStatus;
import com.jobportal.exception.ErrorCode;
import com.jobportal.exception.JobPortalException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.jpa.AppUserRepository;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.repository.jpa.UserJobApplicationRepository;
import com.jobportal.repository.jpa.UserSavedJobRepository;
import com.jobportal.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int PROFILE_JOBS_PAGE_SIZE = 10;

    private final AppUserRepository appUserRepository;
    private final JobRepository jobRepository;
    private final UserSavedJobRepository userSavedJobRepository;
    private final UserJobApplicationRepository userJobApplicationRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(AuthPrincipal principal) {
        AppUser user = currentUser(principal);

        return UserProfileResponse.builder()
                .user(toUserResponse(user))
                .savedCount(userSavedJobRepository.countByUserId(user.getId()))
                .trackedCount(userJobApplicationRepository.countByUserIdAndStatus(user.getId(), ApplicationStatus.TRACKED))
                .appliedCount(userJobApplicationRepository.countByUserIdAndStatus(user.getId(), ApplicationStatus.APPLIED))
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProfileJobResponse> getSavedJobs(AuthPrincipal principal, int page) {
        AppUser user = currentUser(principal);
        Page<ProfileJobResponse> savedJobs = userSavedJobRepository.findByUserId(user.getId(), profilePage(page, "createdAt"))
                .map(saved -> mapJob(saved.getJob(), saved.getCreatedAt(), null, null, null));
        return PagedResponse.of(savedJobs, Map.of());
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProfileJobResponse> getAppliedJobs(AuthPrincipal principal, int page) {
        AppUser user = currentUser(principal);
        Page<ProfileJobResponse> appliedJobs = userJobApplicationRepository
                .findByUserIdAndStatus(user.getId(), ApplicationStatus.APPLIED, profilePage(page, "appliedAt"))
                .map(application -> mapJob(application.getJob(), null, application.getTrackedAt(), application.getAppliedAt(), application.getStatus()));
        return PagedResponse.of(appliedJobs, Map.of());
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProfileJobResponse> getTrackedJobs(AuthPrincipal principal, int page) {
        AppUser user = currentUser(principal);
        Page<ProfileJobResponse> trackedJobs = userJobApplicationRepository
                .findByUserIdAndStatus(user.getId(), ApplicationStatus.TRACKED, profilePage(page, "trackedAt"))
                .map(application -> mapJob(application.getJob(), null, application.getTrackedAt(), null, application.getStatus()));
        return PagedResponse.of(trackedJobs, Map.of());
    }

    @Transactional(readOnly = true)
    public JobTrackingStateResponse getJobState(AuthPrincipal principal, Long jobId) {
        AppUser user = currentUser(principal);
        requireJob(jobId);
        return buildState(user.getId(), jobId);
    }

    @Transactional(readOnly = true)
    public List<JobTrackingStateResponse> getJobStates(AuthPrincipal principal, Collection<Long> jobIds) {
        AppUser user = currentUser(principal);
        Set<Long> uniqueJobIds = jobIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (uniqueJobIds.isEmpty()) return List.of();
        if (uniqueJobIds.size() > 100) {
            throw new JobPortalException(ErrorCode.INVALID_REQUEST, "A maximum of 100 job IDs can be requested at once.", 400);
        }

        Set<Long> savedJobIds = userSavedJobRepository.findByUserIdAndJobIdIn(user.getId(), uniqueJobIds)
                .stream()
                .map(saved -> saved.getJob().getId())
                .collect(Collectors.toSet());
        Map<Long, UserJobApplication> applicationsByJobId = userJobApplicationRepository.findByUserIdAndJobIdIn(user.getId(), uniqueJobIds)
                .stream()
                .collect(Collectors.toMap(application -> application.getJob().getId(), Function.identity()));

        return uniqueJobIds.stream()
                .map(jobId -> {
                    UserJobApplication application = applicationsByJobId.get(jobId);
                    return JobTrackingStateResponse.builder()
                            .jobId(jobId)
                            .saved(savedJobIds.contains(jobId))
                            .tracked(application != null && application.getStatus() == ApplicationStatus.TRACKED)
                            .applied(application != null && application.getStatus() == ApplicationStatus.APPLIED)
                            .trackedAt(application != null ? application.getTrackedAt() : null)
                            .appliedAt(application != null ? application.getAppliedAt() : null)
                            .applicationStatus(application != null ? application.getStatus() : null)
                            .build();
                })
                .toList();
    }

    @Transactional
    public JobTrackingStateResponse saveJob(AuthPrincipal principal, Long jobId) {
        AppUser user = currentUser(principal);
        Job job = requireJob(jobId);
        userSavedJobRepository.findByUserIdAndJobId(user.getId(), jobId)
                .orElseGet(() -> userSavedJobRepository.save(UserSavedJob.builder()
                        .user(user)
                        .job(job)
                        .build()));
        return buildState(user.getId(), jobId);
    }

    @Transactional
    public void removeSavedJob(AuthPrincipal principal, Long jobId) {
        AppUser user = currentUser(principal);
        userSavedJobRepository.findByUserIdAndJobId(user.getId(), jobId)
                .ifPresent(userSavedJobRepository::delete);
    }

    @Transactional
    public JobTrackingStateResponse markApplied(AuthPrincipal principal, Long jobId) {
        AppUser user = currentUser(principal);
        Job job = requireJob(jobId);
        Instant now = Instant.now();
        UserJobApplication application = userJobApplicationRepository.findByUserIdAndJobId(user.getId(), jobId)
                .orElseGet(() -> UserJobApplication.builder()
                        .user(user)
                        .job(job)
                        .status(ApplicationStatus.APPLIED)
                        .trackedAt(now)
                        .appliedAt(now)
                        .build());
        if (application.getStatus() != ApplicationStatus.APPLIED || application.getAppliedAt() == null) {
            application.setStatus(ApplicationStatus.APPLIED);
            application.setAppliedAt(now);
        }
        if (application.getTrackedAt() == null) {
            application.setTrackedAt(now);
        }
        userJobApplicationRepository.save(application);
        return buildState(user.getId(), jobId);
    }

    @Transactional
    public JobTrackingStateResponse trackJob(AuthPrincipal principal, Long jobId) {
        AppUser user = currentUser(principal);
        Job job = requireJob(jobId);
        userJobApplicationRepository.findByUserIdAndJobId(user.getId(), jobId)
                .orElseGet(() -> userJobApplicationRepository.save(UserJobApplication.builder()
                        .user(user)
                        .job(job)
                        .status(ApplicationStatus.TRACKED)
                        .trackedAt(Instant.now())
                        .build()));
        return buildState(user.getId(), jobId);
    }

    @Transactional
    public void removeTracking(AuthPrincipal principal, Long jobId) {
        AppUser user = currentUser(principal);
        userJobApplicationRepository.findByUserIdAndJobId(user.getId(), jobId)
                .ifPresent(userJobApplicationRepository::delete);
    }

    private JobTrackingStateResponse buildState(Long userId, Long jobId) {
        return userJobApplicationRepository.findByUserIdAndJobId(userId, jobId)
                .map(application -> JobTrackingStateResponse.builder()
                        .jobId(jobId)
                        .saved(userSavedJobRepository.existsByUserIdAndJobId(userId, jobId))
                        .tracked(application.getStatus() == ApplicationStatus.TRACKED)
                        .applied(application.getStatus() == ApplicationStatus.APPLIED)
                        .trackedAt(application.getTrackedAt())
                        .appliedAt(application.getAppliedAt())
                        .applicationStatus(application.getStatus())
                        .build())
                .orElseGet(() -> JobTrackingStateResponse.builder()
                        .jobId(jobId)
                        .saved(userSavedJobRepository.existsByUserIdAndJobId(userId, jobId))
                        .tracked(false)
                        .applied(false)
                        .build());
    }

    private PageRequest profilePage(int page, String sortProperty) {
        return PageRequest.of(page, PROFILE_JOBS_PAGE_SIZE,
                Sort.by(Sort.Order.desc(sortProperty), Sort.Order.desc("id")));
    }

    private AppUser currentUser(AuthPrincipal principal) {
        AppUser user = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new JobPortalException(ErrorCode.AUTHENTICATION_REQUIRED, 401));
        if (!user.isEnabled()) {
            throw new JobPortalException(ErrorCode.ACCOUNT_DISABLED, 403);
        }
        return user;
    }

    private Job requireJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> ResourceNotFoundException.job(jobId));
    }

    private AuthUserResponse toUserResponse(AppUser user) {
        return AuthUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    private ProfileJobResponse mapJob(Job job, Instant savedAt, Instant trackedAt, Instant appliedAt, ApplicationStatus applicationStatus) {
        return ProfileJobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompany() != null ? job.getCompany().getName() : null)
                .companySlug(job.getCompany() != null ? job.getCompany().getSlug() : null)
                .companyLogoUrl(job.getCompany() != null ? job.getCompany().getLogoUrl() : null)
                .country(job.getCountry())
                .city(job.getCity())
                .workMode(job.getWorkMode() != null ? job.getWorkMode().name() : null)
                .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                .visaStatus(job.getVisaStatus() != null ? job.getVisaStatus().name() : null)
                .applyUrl(job.getApplyUrl())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .salaryCurrency(job.getSalaryCurrency())
                .tags(parseTags(job.getTags()))
                .postedAt(job.getPostedAt())
                .savedAt(savedAt)
                .trackedAt(trackedAt)
                .appliedAt(appliedAt)
                .applicationStatus(applicationStatus)
                .build();
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}
