package com.jobportal.service.auth;

public record GithubIdentity(String subject, String email, String name) {
}
