package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.TokenResponse;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.AppUserToken;
import com.api.v1.bearerauth.model.entity.Customer;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.service.FirebaseTokenVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.security.SecureRandom;
import java.util.Locale;
import com.api.v1.bearerauth.model.request.AppUserAddRequest;
import com.api.v1.bearerauth.model.request.AuthRequest;
import com.api.v1.bearerauth.model.request.RefreshTokenRequest;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.AppUserTokenService;
import com.api.v1.bearerauth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AppUserService appUserService;
    private final AppUserTokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional
    public AppUserDto create(AppUserAddRequest request) {
        AppUserDto dto = appUserService.create(request);
        AppUser user = appUserRepository.findByEmail(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create account"));
        sendVerificationCode(user);
        return dto;
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String code) {
        AppUser user = appUserRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new CustomException(HttpStatus.BAD_REQUEST, "Invalid email or verification code"));

        if (user.isEnabled()) {
            return;
        }
        if (user.getEmailVerificationCodeHash() == null
                || user.getEmailVerificationExpiresAt() == null
                || user.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(code.trim(), user.getEmailVerificationCodeHash())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }

        user.setEnabled(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationLastSentAt(null);
        appUserRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerificationCode(String email) {
        AppUser user = appUserRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Account not found"));
        if (user.isEnabled()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Email is already verified");
        }
        if (user.getEmailVerificationLastSentAt() != null
                && user.getEmailVerificationLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new CustomException(HttpStatus.TOO_MANY_REQUESTS, "Please wait 60 seconds before requesting another code");
        }
        sendVerificationCode(user);
    }

    private void sendVerificationCode(AppUser user) {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(10));
        user.setEmailVerificationLastSentAt(LocalDateTime.now());
        appUserRepository.save(user);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Foodie email verification code");
        message.setText("Your Foodie verification code is: " + code
                + "\n\nThis code expires in 10 minutes. If you did not request this, you can ignore this email.");
        mailSender.send(message);
    }

    @Override
    @Transactional
    public TokenResponse login(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!user.isEnabled()) {
            throw new CustomException(HttpStatus.FORBIDDEN, "Please verify your email before logging in");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException e) {
            throw new CustomException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        tokenService.deleteAll(tokenService.findAllByUser(user));

        TokenResponse token = jwtService.generateAccess(user);
        saveRefresh(user, token.getRefresh());
        return token;
    }

    @Override
    @Transactional
    public TokenResponse googleLogin(String firebaseIdToken) {
        FirebaseTokenVerifier.VerifiedGoogleUser identity =
                firebaseTokenVerifier.verify(firebaseIdToken);

        String email = identity.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = appUserRepository.findByEmail(email).orElseGet(() -> {
            AppUser newUser = new AppUser();
            newUser.setEmail(email);
            newUser.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            newUser.setPasswordConfigured(false);
            newUser.setRole(Roles.USER);
            newUser.setEnabled(true);

            Customer customer = new Customer();
            String displayName = identity.name();
            if (displayName == null || displayName.isBlank()) {
                displayName = email.substring(0, email.indexOf('@'));
            }
            customer.setCustomerName(displayName.trim());
            customer.setUser(newUser);
            newUser.setCustomer(customer);
            return appUserRepository.save(newUser);
        });

        if (!user.isEnabled()) {
            // Firebase has verified this Google identity; allow the verified Google account to proceed.
            user.setEnabled(true);
            user.setEmailVerificationCodeHash(null);
            user.setEmailVerificationExpiresAt(null);
            user.setEmailVerificationLastSentAt(null);
            appUserRepository.save(user);
        }

        tokenService.deleteAll(tokenService.findAllByUser(user));
        TokenResponse token = jwtService.generateAccess(user);
        token.setRequiresPasswordSetup(!user.isPasswordConfigured());
        saveRefresh(user, token.getRefresh());
        return token;
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        String refresh = tokenService.existsByRefresh(request.getRefresh());
        String username = jwtService.extractUsername(refresh);
        UserDetails user = appUserService.loadUserByUsername(username);

        if (!jwtService.validateRefreshToken(refresh, user)) {
            throw new CustomException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        AppUser appUser = (AppUser) user;
        tokenService.deleteAll(tokenService.findAllByUser(appUser));

        String access = jwtService.generateAccessOnly(user);
        String newRefresh = jwtService.generateRefreshOnly(user);
        saveRefresh(appUser, newRefresh);

        return TokenResponse.builder()
                .access(access)
                .refresh(newRefresh)
                .issuedAt(new java.util.Date())
                .expiredAt(new java.util.Date(System.currentTimeMillis() + 900000L))
                .build();
    }

    private void saveRefresh(AppUser user, String refresh) {
        AppUserToken token = new AppUserToken();
        token.setRefresh(refresh);
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        tokenService.save(token);
    }
}
