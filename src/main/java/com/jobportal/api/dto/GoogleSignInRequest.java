package com.jobportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleSignInRequest {

    @NotBlank(message = "Google credential is required")
    private String credential;
}
