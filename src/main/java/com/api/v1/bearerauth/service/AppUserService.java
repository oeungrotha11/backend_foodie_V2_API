package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.request.*;
import com.api.v1.bearerauth.model.response.AppUserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AppUserService extends UserDetailsService {
    Page<AppUserDto> list(int page, int size, Sort.Direction direction);
    AppUserDto get(Long id);
    AppUserDto current();
    AppUserDto create(AppUserAddRequest request);
    AppUserDto update(AppUserUpdateRequest request);
    AppUserDto update(Long id, AppUserUpdateRequest request);
    void delete(Long id);
    void deleteCurrentUser();
    void deleteByAdmin(Long id);
    void logout();
    void changePassword(ChangePasswordRequest request);
    void setPassword(SetPasswordRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
