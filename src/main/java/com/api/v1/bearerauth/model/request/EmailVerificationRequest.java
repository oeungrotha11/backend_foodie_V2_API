package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EmailVerificationRequest {
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email address")
    private String email;

    @NotBlank(message = "Verification code cannot be blank")
    @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must contain 6 digits")
    private String code;
}
