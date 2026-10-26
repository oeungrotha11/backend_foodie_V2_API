package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {
    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;
}
