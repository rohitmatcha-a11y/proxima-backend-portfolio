package com.jobportal.api.dto;

import com.jobportal.domain.enums.UserRole;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthUserResponse {
    Long id;
    String name;
    String email;
    UserRole role;
}
