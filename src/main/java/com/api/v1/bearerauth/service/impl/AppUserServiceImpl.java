package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Customer;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.*;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.CustomerRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.AppUserTokenService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {
    private final AppUserRepository appUserRepository;
    private final CustomerRepository customerRepository;
    private final AppUserTokenService tokenService;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper mapper;
    private final JavaMailSender mailSender;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return appUserRepository.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppUserDto> list(int page, int size, Sort.Direction direction) {
        return appUserRepository.findAll(PageRequest.of(page - 1, size, Sort.by(direction, "id"))).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDto get(Long id) {
        return toDto(findUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDto current() {
        return toDto(currentUserService.getCurrentUser());
    }

    @Override
    @Transactional
    public void logout() {
        AppUser user = currentUserService.getCurrentUser();
        tokenService.deleteAll(tokenService.findAllByUser(user));
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public AppUserDto create(AppUserAddRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (appUserRepository.findByEmail(email).isPresent()) {
            throw new CustomException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (!request.getPassword().equals(request.getConfirm())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Roles.USER);
        user.setEnabled(false);

        Customer customer = new Customer();
        customer.setCustomerName(request.getUsername().trim());
        customer.setUser(user);

        user.setCustomer(customer);

        return toDto(appUserRepository.save(user));
    }

    @Override
    @Transactional
    public AppUserDto update(AppUserUpdateRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        return updateUser(user, request);
    }

    @Override
    @Transactional
    public AppUserDto update(Long id, AppUserUpdateRequest request) {
        AppUser user = findUser(id);
        return updateUser(user, request);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AppUser user = findUser(id);

        if (currentUserService.getCurrentUser().getId().equals(id)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "You cannot delete your own account while logged in");
        }

        Customer customer = user.getCustomer();

        if (customer != null && customer.getOrders() != null && !customer.getOrders().isEmpty()) {
            throw new CustomException(HttpStatus.CONFLICT, "Cannot delete user because customer has orders");
        }

        tokenService.deleteAll(tokenService.findAllByUser(user));
        appUserRepository.delete(user);
    }

    @Override
    @Transactional
    public void deleteCurrentUser() {
        AppUser currentUser = currentUserService.getCurrentUser();

        deleteUser(currentUser.getId());
    }

    @Override
    @Transactional
    public void deleteByAdmin(Long id) {
        AppUser currentUser = currentUserService.getCurrentUser();

        if (currentUser.getId().equals(id)) {
            throw new CustomException(
                    HttpStatus.BAD_REQUEST,
                    "Admin cannot delete their own account through this endpoint"
            );
        }

        deleteUser(id);
    }

    private void deleteUser(Long id) {
        AppUser user = findUser(id);

        Customer customer = user.getCustomer();

        if (customer != null
                && customer.getOrders() != null
                && !customer.getOrders().isEmpty()) {
            throw new CustomException(
                    HttpStatus.CONFLICT,
                    "Cannot delete user because customer has orders"
            );
        }

        tokenService.deleteAll(tokenService.findAllByUser(user));

        appUserRepository.delete(user);
    }


    @Override
    @Transactional
    public void setPassword(SetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match");
        }
        AppUser user = currentUserService.getCurrentUser();
        if (user.isPasswordConfigured()) {
            throw new CustomException(HttpStatus.BAD_REQUEST,
                    "A Foodie password is already configured. Use the change-password feature instead.");
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordConfigured(true);
        appUserRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName().trim().toLowerCase();

        AppUser user = appUserRepository.findByEmail(email).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "New password and confirm password do not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        appUserRepository.save(user);

        // Revoke all stored refresh tokens for this user
        tokenService.deleteAll(tokenService.findAllByUser(user));
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(email).orElse(null);

        // Use the same public response whether the email exists or not to reduce account enumeration.
        if (user == null) {
            return;
        }

        if (user.getPasswordResetLastSentAt() != null
                && user.getPasswordResetLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new CustomException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait 60 seconds before requesting another reset code");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        user.setPasswordResetCodeHash(passwordEncoder.encode(code));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(10));
        user.setPasswordResetLastSentAt(LocalDateTime.now());
        appUserRepository.save(user);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Foodie password reset code");
        message.setText("Your Foodie password reset code is: " + code
                + "\n\nThis code expires in 10 minutes. If you did not request a password reset, ignore this email.");
        mailSender.send(message);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match");
        }

        String email = request.getEmail().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code"));

        if (user.getPasswordResetCodeHash() == null
                || user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(request.getCode().trim(), user.getPasswordResetCodeHash())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordConfigured(true);
        user.setPasswordResetCodeHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setPasswordResetLastSentAt(null);
        appUserRepository.save(user);

        // Revoke refresh tokens so the user signs in again after password recovery.
        tokenService.deleteAll(tokenService.findAllByUser(user));
    }



    private AppUserDto updateUser(AppUser user, AppUserUpdateRequest request) {
        // Email is the account's login identity and must not be changed through profile editing.
        Customer customer = user.getCustomer();

        if (customer == null) {
            customer = new Customer();
            customer.setUser(user);
            user.setCustomer(customer);
        }

        customer.setCustomerName(request.getUsername().trim());
        customer.setAddress(request.getAddress() == null ? "" : request.getAddress().trim());
        customer.setPhoneNumber(request.getPhone().trim());
        customer.setCity(request.getCity().trim());

        return toDto(appUserRepository.save(user));
    }

    private AppUser findUser(Long id) {
        return appUserRepository.findById(id).orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AppUserDto toDto(AppUser user) {
        AppUserDto dto = new AppUserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        if (user.getCustomer() != null) {
            dto.setCustomerId(user.getCustomer().getCustomerId());
            dto.setUsername(user.getCustomer().getCustomerName());
            dto.setAddress(user.getCustomer().getAddress());
            dto.setCity(user.getCustomer().getCity());
            dto.setPhoneNumber(user.getCustomer().getPhoneNumber());
        } else {
            dto.setUsername(user.getEmail());
        }

        return dto;
    }
}

