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
}
