package com.jobportal.api.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserProfileResponse {
    AuthUserResponse user;
    long savedCount;
    long trackedCount;
    long appliedCount;
}
