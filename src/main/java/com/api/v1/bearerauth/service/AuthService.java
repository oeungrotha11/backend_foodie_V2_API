package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.common.response.TokenResponse;
import com.api.v1.bearerauth.model.request.AppUserAddRequest;
import com.api.v1.bearerauth.model.request.AuthRequest;
import com.api.v1.bearerauth.model.request.RefreshTokenRequest;
import com.api.v1.bearerauth.model.response.AppUserDto;

public interface AuthService {
    AppUserDto create(AppUserAddRequest request);
    void verifyEmail(String email, String code);
    void resendVerificationCode(String email);
    TokenResponse login(AuthRequest request);
    TokenResponse googleLogin(String firebaseIdToken);
    TokenResponse refresh(RefreshTokenRequest request);
}
