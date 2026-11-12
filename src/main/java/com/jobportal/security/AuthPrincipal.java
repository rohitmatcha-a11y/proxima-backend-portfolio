package com.jobportal.security;

import com.jobportal.domain.enums.UserRole;

public record AuthPrincipal(Long userId, String email, UserRole role) {
}
