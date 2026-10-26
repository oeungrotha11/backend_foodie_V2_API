package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.common.response.TokenResponse;
import com.api.v1.bearerauth.model.request.*;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.AuthService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AppUserService appUserService;

    @PostMapping("/register")
    public ResponseEntity<SuccessResponse<AppUserDto>> register(@Valid @RequestBody AppUserAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.success(HttpStatus.CREATED, authService.create(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<TokenResponse>> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, authService.login(request)));
    }

    @PostMapping("/google")
    public ResponseEntity<SuccessResponse<TokenResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponseUtil.success(
                HttpStatus.OK, authService.googleLogin(request.getFirebaseIdToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, authService.refresh(request)));
    }


    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        appUserService.forgotPassword(request);

        return ResponseEntity.ok(
                Map.of("message", "Email found. You can reset your password.")
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        appUserService.resetPassword(request);

        return ResponseEntity.ok(
                Map.of("message", "Password reset successfully.")
        );
    }

}
