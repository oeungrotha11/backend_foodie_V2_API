package com.api.v1.bearerauth.model.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Verification code cannot be blank")
    @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{6}$", message = "Verification code must contain 6 digits")
    private String code;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password cannot be blank")
    private String confirmPassword;
}
