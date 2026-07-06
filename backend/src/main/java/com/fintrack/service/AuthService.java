package com.fintrack.service;

import com.fintrack.dto.AuthDto;
import com.fintrack.entity.AccountToken;
import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import com.fintrack.security.JwtUtil;
import com.fintrack.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AccountTokenService accountTokenService;
    private final MailService mailService;

    /** Base URL used in emailed links; the SPA handles the /verify-email and /reset-password routes. */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** An access token response plus the refresh token destined for the httpOnly cookie. */
    public record AuthSession(AuthDto.AuthResponse response, RefreshTokenService.IssuedToken refreshToken) {
    }

    public AuthSession register(AuthDto.RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
        sendVerificationEmail(user);
        return createSession(user);
    }

    public AuthSession login(AuthDto.LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        loginAttemptService.checkNotLocked(email);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email);
            throw e;
        }
        loginAttemptService.recordSuccess(email);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return createSession(user);
    }

    /** Rotates the presented refresh token and mints a fresh access token. */
    public AuthSession refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = rotation.user();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateToken(userDetails);
        return new AuthSession(
                new AuthDto.AuthResponse(accessToken, user.getName(), user.getEmail(), user.getId(),
                        Boolean.TRUE.equals(user.getEmailVerified())),
                rotation.newToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    public User getCurrentUser(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public void verifyEmail(String rawToken) {
        User user = accountTokenService.consume(rawToken, AccountToken.Purpose.VERIFY_EMAIL);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    public void resendVerification(String email) {
        User user = getCurrentUser(email);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return; // nothing to do; don't spam
        }
        sendVerificationEmail(user);
    }

    /** Always succeeds from the caller's perspective so emails can't be enumerated. */
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email)).ifPresent(user -> {
            String token = accountTokenService.issue(user, AccountToken.Purpose.RESET_PASSWORD);
            mailService.send(user.getEmail(), "Reset your Tally password",
                    "Hi " + user.getName() + ",\n\n"
                            + "Someone (hopefully you) asked to reset your Tally password. "
                            + "This link is valid for 1 hour:\n\n"
                            + frontendUrl + "/reset-password?token=" + token + "\n\n"
                            + "If you didn't request this, you can safely ignore this email.");
        });
    }

    public void resetPassword(String rawToken, String newPassword) {
        User user = accountTokenService.consume(rawToken, AccountToken.Purpose.RESET_PASSWORD);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // The password change kills every existing session.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private void sendVerificationEmail(User user) {
        String token = accountTokenService.issue(user, AccountToken.Purpose.VERIFY_EMAIL);
        mailService.send(user.getEmail(), "Verify your Tally email",
                "Hi " + user.getName() + ",\n\n"
                        + "Confirm your email address to finish setting up Tally. "
                        + "This link is valid for 48 hours:\n\n"
                        + frontendUrl + "/verify-email?token=" + token + "\n\n"
                        + "If you didn't create a Tally account, you can ignore this email.");
    }

    private AuthSession createSession(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateToken(userDetails);
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user);
        return new AuthSession(
                new AuthDto.AuthResponse(accessToken, user.getName(), user.getEmail(), user.getId(),
                        Boolean.TRUE.equals(user.getEmailVerified())),
                refreshToken);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
