package com.fintrack.controller;

import com.fintrack.dto.AuthDto;
import com.fintrack.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, refresh, and logout endpoints")
public class AuthController {

    static final String REFRESH_COOKIE = "tally_refresh";

    private final AuthService authService;

    /**
     * Secure cookies require HTTPS; disabled in the local profile where the app
     * is served over plain http. Never disable in production.
     */
    @Value("${auth.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return withRefreshCookie(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive an access token; the refresh token is set as an httpOnly cookie")
    public ResponseEntity<AuthDto.AuthResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    public ResponseEntity<AuthDto.AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token");
        }
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address using the token from the verification email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthDto.VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Send a fresh verification email to the logged-in user")
    public ResponseEntity<Void> resendVerification(org.springframework.security.core.Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        authService.resendVerification(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a password-reset link; responds 204 whether or not the email exists")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the token from the reset email; revokes all sessions")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDto.ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthDto.AuthResponse> withRefreshCookie(AuthService.AuthSession session) {
        ResponseCookie cookie = refreshCookie(session.refreshToken().rawToken(), session.refreshToken().maxAge());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(session.response());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                // Only auth endpoints ever need the refresh token; keeping the
                // path narrow keeps it off every other request.
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
