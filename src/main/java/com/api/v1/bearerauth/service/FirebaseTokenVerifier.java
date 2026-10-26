package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class FirebaseTokenVerifier {

    public record VerifiedGoogleUser(String email, String name) {}

    public VerifiedGoogleUser verify(String idToken) {
        try {
            FirebaseApp app = getOrInitializeFirebaseApp();
            FirebaseToken token = FirebaseAuth.getInstance(app).verifyIdToken(idToken);

            Object provider = token.getClaims().get("firebase");
            if (!(provider instanceof java.util.Map<?, ?> firebaseClaims)) {
                throw new CustomException(HttpStatus.UNAUTHORIZED, "Invalid Firebase identity token");
            }
            Object signInProvider = firebaseClaims.get("sign_in_provider");
            if (!"google.com".equals(signInProvider)) {
                throw new CustomException(HttpStatus.UNAUTHORIZED, "Please sign in with Google");
            }

            String email = token.getEmail();
            if (email == null || email.isBlank()) {
                throw new CustomException(HttpStatus.UNAUTHORIZED, "Google account must have an email address");
            }
            Object verified = token.getClaims().get("email_verified");
            if (!Boolean.TRUE.equals(verified)) {
                throw new CustomException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
            }

            return new VerifiedGoogleUser(email, token.getName());
        } catch (CustomException exception) {
            throw exception;
        } catch (IllegalStateException | IOException exception) {
            throw new CustomException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase Admin is not configured. Set GOOGLE_APPLICATION_CREDENTIALS on the server.");
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new CustomException(HttpStatus.UNAUTHORIZED, "Invalid or expired Firebase ID token");
        }
    }

    private FirebaseApp getOrInitializeFirebaseApp() throws IOException {
        synchronized (FirebaseTokenVerifier.class) {
            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.getInstance();
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }
}
