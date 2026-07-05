package com.fintrack;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthTransactionIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long transactionId;

    @BeforeEach
    void disableClientCookieJar() {
        // Cookie handling must stay explicit: the refresh-rotation tests send
        // stale cookies on purpose, which an automatic cookie jar would replace.
        rest.getRestTemplate().setRequestFactory(
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(
                        org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                                .disableCookieManagement()
                                .build()));
    }

    private static final String EMAIL = "inttest@example.com";
    private static final String PASSWORD = "testpassword123";

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @Order(1)
    void register_shouldSucceedAndReturnToken() {
        Map<String, String> body = Map.of("name", "Integration Test", "email", EMAIL, "password", PASSWORD);
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat((String) response.getBody().get("token")).isNotBlank();
    }

    @Test
    @Order(2)
    void login_shouldReturnValidToken() {
        Map<String, String> body = Map.of("email", EMAIL, "password", PASSWORD);
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = (String) response.getBody().get("token");
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(3)
    void createTransaction_shouldReturn200WithId() {
        Map<String, Object> body = Map.of(
                "description", "Groceries",
                "amount", 85.00,
                "type", "EXPENSE",
                "category", "Food",
                "date", LocalDate.now().toString()
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, authHeaders());
        ResponseEntity<Map> response = rest.exchange("/api/transactions", HttpMethod.POST, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        transactionId = ((Number) response.getBody().get("id")).longValue();
        assertThat(transactionId).isPositive();
        assertThat(response.getBody().get("description")).isEqualTo("Groceries");
        assertThat(response.getBody().get("category")).isEqualTo("Food");
    }

    @Test
    @Order(4)
    void getAllTransactions_shouldReturnPageWithCreatedTransaction() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        ResponseEntity<Map> response = rest.exchange("/api/transactions", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat((Integer) response.getBody().get("totalElements")).isGreaterThan(0);
    }

    @Test
    @Order(5)
    void updateTransaction_shouldChangeDescription() {
        Map<String, Object> body = Map.of(
                "description", "Groceries Updated",
                "amount", 90.00,
                "type", "EXPENSE",
                "category", "Food",
                "date", LocalDate.now().toString()
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, authHeaders());
        ResponseEntity<Map> response = rest.exchange("/api/transactions/" + transactionId, HttpMethod.PUT, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("description")).isEqualTo("Groceries Updated");
        assertThat(((Number) response.getBody().get("amount")).doubleValue()).isEqualTo(90.00);
    }

    @Test
    @Order(6)
    void deleteTransaction_shouldReturn204() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        ResponseEntity<Void> response = rest.exchange(
                "/api/transactions/" + transactionId, HttpMethod.DELETE, request, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(7)
    void accessWithoutToken_shouldBeDenied() {
        ResponseEntity<String> response = rest.getForEntity("/api/transactions", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(8)
    void refreshFlow_rotatesCookieAndRejectsReplay() {
        // Login sets the refresh cookie
        Map<String, String> body = Map.of("email", EMAIL, "password", PASSWORD);
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login", body, Map.class);
        String originalCookie = extractRefreshCookie(login);
        assertThat(originalCookie).isNotBlank();
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly");

        // Refresh returns a new access token and rotates the cookie
        ResponseEntity<Map> refreshed = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                withCookie(originalCookie), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) refreshed.getBody().get("token")).isNotBlank();
        String rotatedCookie = extractRefreshCookie(refreshed);
        assertThat(rotatedCookie).isNotEqualTo(originalCookie);

        // Replaying the consumed cookie is rejected (and revokes the family)
        ResponseEntity<Map> replay = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                withCookie(originalCookie), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The rotated cookie was revoked along with the rest of the family
        ResponseEntity<Map> afterReplay = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                withCookie(rotatedCookie), Map.class);
        assertThat(afterReplay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(9)
    void refreshWithoutCookie_shouldReturn401() {
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/refresh", null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpEntity<Void> withCookie(String refreshCookieValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "tally_refresh=" + refreshCookieValue);
        return new HttpEntity<>(headers);
    }

    private String extractRefreshCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(c -> c.startsWith("tally_refresh="))
                .map(c -> c.substring("tally_refresh=".length(), c.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }
}
