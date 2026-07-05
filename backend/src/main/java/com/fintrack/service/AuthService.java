package com.fintrack.service;

import com.fintrack.dto.AuthDto;
import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
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
        return createSession(user);
    }

    public AuthSession login(AuthDto.LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

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
                new AuthDto.AuthResponse(accessToken, user.getName(), user.getEmail(), user.getId()),
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

    private AuthSession createSession(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateToken(userDetails);
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user);
        return new AuthSession(
                new AuthDto.AuthResponse(accessToken, user.getName(), user.getEmail(), user.getId()),
                refreshToken);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
