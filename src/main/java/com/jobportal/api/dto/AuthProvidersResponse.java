package com.jobportal.api.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthProvidersResponse {
    boolean google;
    boolean github;
}
