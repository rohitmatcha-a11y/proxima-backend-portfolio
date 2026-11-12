package com.jobportal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AuthSessionResponse {
    AuthUserResponse user;
    Instant expiresAt;
}
