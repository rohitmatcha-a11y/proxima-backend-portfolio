package com.jobportal.service.auth;

public record GoogleIdentity(String subject, String email, String name) {
}
