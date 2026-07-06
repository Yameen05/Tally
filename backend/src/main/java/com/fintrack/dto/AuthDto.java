package com.fintrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be 100 characters or fewer")
        private String name;

        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        // Length cap avoids excessive BCrypt work; min 8 with a letter and a digit
        // is a reasonable baseline against trivially guessable passwords.
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @Email
        @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String name;
        private String email;
        private Long userId;
        private boolean emailVerified;
    }

    @Data
    public static class ForgotPasswordRequest {
        @Email
        @NotBlank
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        private String newPassword;
    }

    @Data
    public static class VerifyEmailRequest {
        @NotBlank
        private String token;
    }
}
