package com.jobportal.api.controller;

import com.jobportal.api.dto.JobTrackingStateResponse;
import com.jobportal.api.dto.PagedResponse;
import com.jobportal.api.dto.ProfileJobResponse;
import com.jobportal.api.dto.UserProfileResponse;
import com.jobportal.security.AuthPrincipal;
import com.jobportal.service.profile.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Min;
import java.util.List;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "Saved jobs and application tracking for the signed-in user")
@RequiredArgsConstructor
@Validated
public class ProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(summary = "Get the signed-in user's profile summary")
    public UserProfileResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
        return userProfileService.getProfile(principal);
    }

    @GetMapping("/saved-jobs")
    @Operation(summary = "Get the signed-in user's saved jobs, 10 per page")
    public PagedResponse<ProfileJobResponse> savedJobs(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page) {
        return userProfileService.getSavedJobs(principal, page);
    }

    @GetMapping("/applications")
    @Operation(summary = "Get the signed-in user's confirmed applications, 10 per page")
    public PagedResponse<ProfileJobResponse> applications(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page) {
        return userProfileService.getAppliedJobs(principal, page);
    }

    @GetMapping("/tracked-jobs")
    @Operation(summary = "Get the signed-in user's jobs being tracked, 10 per page")
    public PagedResponse<ProfileJobResponse> trackedJobs(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @RequestParam(defaultValue = "0") @Min(0) int page) {
        return userProfileService.getTrackedJobs(principal, page);
    }

    @GetMapping("/jobs/states")
    @Operation(summary = "Get the signed-in user's tracking states for a set of jobs")
    public List<JobTrackingStateResponse> jobStates(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @RequestParam(name = "jobId") List<Long> jobIds) {
        return userProfileService.getJobStates(principal, jobIds);
    }

    @GetMapping("/jobs/{jobId}/state")
    @Operation(summary = "Get the signed-in user's tracking state for one job")
    public JobTrackingStateResponse jobState(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long jobId) {
        return userProfileService.getJobState(principal, jobId);
    }

    @PutMapping("/saved-jobs/{jobId}")
    @Operation(summary = "Save a job")
    public JobTrackingStateResponse saveJob(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long jobId) {
        return userProfileService.saveJob(principal, jobId);
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    @Operation(summary = "Remove a saved job")
    public ResponseEntity<Void> removeSavedJob(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable Long jobId) {
        userProfileService.removeSavedJob(principal, jobId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/applications/{jobId}")
    @Operation(summary = "Confirm that a job application was submitted")
    public JobTrackingStateResponse markApplied(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable Long jobId) {
        return userProfileService.markApplied(principal, jobId);
    }

    @PutMapping("/tracked-jobs/{jobId}")
    @Operation(summary = "Track a job after opening its company application")
    public JobTrackingStateResponse trackJob(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long jobId) {
        return userProfileService.trackJob(principal, jobId);
    }

    @DeleteMapping("/applications/{jobId}")
    @Operation(summary = "Remove a job from application tracking")
    public ResponseEntity<Void> removeApplication(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable Long jobId) {
        userProfileService.removeTracking(principal, jobId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tracked-jobs/{jobId}")
    @Operation(summary = "Remove a job from application tracking")
    public ResponseEntity<Void> removeTracking(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable Long jobId) {
        userProfileService.removeTracking(principal, jobId);
        return ResponseEntity.noContent().build();
    }
}
