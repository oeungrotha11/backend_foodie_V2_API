package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailVerificationResendRequest {
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email address")
    private String email;
}
