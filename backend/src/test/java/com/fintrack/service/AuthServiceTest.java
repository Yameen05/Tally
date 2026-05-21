package com.fintrack.service;

import com.fintrack.dto.AuthDto;
import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsServiceImpl userDetailsService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Yameen")
                .email("yameen@example.com")
                .password("hashed-pw")
                .build();

        mockUserDetails = new org.springframework.security.core.userdetails.User(
                "yameen@example.com", "hashed-pw", List.of()
        );
    }

    @Test
    void register_newEmail_shouldReturnAuthResponse() {
        when(userRepository.existsByEmail("yameen@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userDetailsService.loadUserByUsername("yameen@example.com")).thenReturn(mockUserDetails);
        when(jwtUtil.generateToken(mockUserDetails)).thenReturn("jwt-token");

        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest();
        request.setName("Yameen");
        request.setEmail("yameen@example.com");
        request.setPassword("secret123");

        AuthDto.AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("yameen@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_shouldThrowConflict() {
        when(userRepository.existsByEmail("yameen@example.com")).thenReturn(true);

        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest();
        request.setEmail("yameen@example.com");
        request.setPassword("secret123");
        request.setName("Yameen");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void login_validCredentials_shouldReturnAuthResponse() {
        when(userRepository.findByEmail("yameen@example.com")).thenReturn(Optional.of(testUser));
        when(userDetailsService.loadUserByUsername("yameen@example.com")).thenReturn(mockUserDetails);
        when(jwtUtil.generateToken(mockUserDetails)).thenReturn("jwt-token");

        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEmail("yameen@example.com");
        request.setPassword("secret123");

        AuthDto.AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getName()).isEqualTo("Yameen");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_invalidCredentials_shouldThrowBadCredentials() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEmail("yameen@example.com");
        request.setPassword("wrong-pw");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
