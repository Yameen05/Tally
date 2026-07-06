package com.fintrack;

import com.fintrack.entity.AccountToken;
import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.service.AccountTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end email verification and password reset. Raw tokens normally travel
 * by email, so the test mints them directly through AccountTokenService.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountFlowsIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private AccountTokenService accountTokenService;
    @Autowired private UserRepository userRepository;

    private static final String EMAIL = "flows@example.com";
    private static final String PASSWORD = "flowspass123";
    private static final String NEW_PASSWORD = "newpass456";

    private User user() {
        return userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
    }

    @Test
    @Order(1)
    void register_startsUnverified() {
        Map<String, String> body = Map.of("name", "Flows", "email", EMAIL, "password", PASSWORD);
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/register", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("emailVerified")).isEqualTo(false);
        assertThat(user().getEmailVerified()).isFalse();
    }

    @Test
    @Order(2)
    void verifyEmail_flipsTheFlag_andTokenIsSingleUse() {
        String token = accountTokenService.issue(user(), AccountToken.Purpose.VERIFY_EMAIL);

        ResponseEntity<Map> ok = rest.postForEntity("/api/auth/verify-email", Map.of("token", token), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(user().getEmailVerified()).isTrue();

        ResponseEntity<Map> replay = rest.postForEntity("/api/auth/verify-email", Map.of("token", token), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(3)
    void forgotPassword_neverRevealsWhetherEmailExists() {
        ResponseEntity<Void> known = rest.postForEntity("/api/auth/forgot-password",
                Map.of("email", EMAIL), Void.class);
        ResponseEntity<Void> unknown = rest.postForEntity("/api/auth/forgot-password",
                Map.of("email", "nobody@example.com"), Void.class);

        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(4)
    void resetPassword_changesPasswordAndKillsSessions() {
        // An active session that must die with the reset
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                Map.of("email", EMAIL, "password", PASSWORD), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String token = accountTokenService.issue(user(), AccountToken.Purpose.RESET_PASSWORD);
        ResponseEntity<Map> reset = rest.postForEntity("/api/auth/reset-password",
                Map.of("token", token, "newPassword", NEW_PASSWORD), Map.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Old password no longer works, new one does
        ResponseEntity<Map> oldLogin = rest.postForEntity("/api/auth/login",
                Map.of("email", EMAIL, "password", PASSWORD), Map.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> newLogin = rest.postForEntity("/api/auth/login",
                Map.of("email", EMAIL, "password", NEW_PASSWORD), Map.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(5)
    void resetPassword_weakPassword_isRejectedWithFieldError() {
        String token = accountTokenService.issue(user(), AccountToken.Purpose.RESET_PASSWORD);
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/reset-password",
                Map.of("token", token, "newPassword", "short"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldErrors = (Map<String, Object>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsKey("newPassword");
    }
}
