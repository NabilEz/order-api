package com.nabil.order_api.integrationTest;


import com.nabil.order_api.domain.OrderRepository;
import com.nabil.order_api.dto.CreateOrderRequest;
import com.nabil.order_api.security.dto.LoginRequest;
import com.nabil.order_api.security.dto.AuthResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OrderRepository orderRepository;

    private static String accessToken;
    private static Long createdOrderId;

    // ── helper ─────────────────────────────────────────────────────────────

    private HttpHeaders authHeaders() {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ── setup ──────────────────────────────────────────────────────────────

    @BeforeAll
    static void login(@Autowired TestRestTemplate rest) {
        // il DataInitializer semina gli utenti all'avvio — usiamo quello
        var req = new LoginRequest("user@demo.com", "User1234!");
        var response = rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(req, jsonHeaders()),
                AuthResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        accessToken = response.getBody().accessToken();
    }

    private static HttpHeaders jsonHeaders() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ── test ───────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/orders → crea ordine con stato PENDING")
    void crea_ordine() {
        var req = new CreateOrderRequest("cust-1", "prod-42", new BigDecimal("99.90"));
        var response = rest.exchange(
                "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("PENDING");
        assertThat(response.getBody()).contains("cust-1");

        // salva l'ID per i test successivi
        createdOrderId = 1L;
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/orders → lista non vuota dopo creazione")
    void lista_ordini_non_vuota() {
        var response = rest.exchange(
                "/api/orders",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("cust-1");
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/orders/99999 → 404 strutturato")
    void ordine_non_trovato_restituisce_404() {
        var response = rest.exchange(
                "/api/orders/99999",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"status\":404");
        assertThat(response.getBody()).contains("Not Found");
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/orders → 400 su importo negativo")
    void validazione_importo_negativo() {
        var req = new CreateOrderRequest("cust-1", "prod-42", new BigDecimal("-10"));
        var response = rest.exchange(
                "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("details");
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/orders → 403 senza token")
    void richiesta_senza_token_restituisce_403() {
        var response = rest.exchange(
                "/api/orders",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(6)
    @DisplayName("PATCH /api/orders/{id}/ship → stato SHIPPED con tracking")
    void spedisce_ordine() {
        var body = """
                {"courier":"DHL","trackingCode":"1Z999AA1"}
                """;

        var response = rest.exchange(
                "/api/orders/1/ship",
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SHIPPED");
        assertThat(response.getBody()).contains("DHL");
    }

    @Test
    @Order(7)
    @DisplayName("PATCH /api/orders/{id}/cancel → stato CANCELLED con motivo")
    void cancella_ordine() {
        var body = """
                {"reason":"prodotto esaurito"}
                """;

        var response = rest.exchange(
                "/api/orders/1/cancel",
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("CANCELLED");
        assertThat(response.getBody()).contains("prodotto esaurito");
    }

//    @Test
//    @Order(8)
//    @DisplayName("DB — verifica persistenza diretta via repository")
//    void persistenza_diretta_sul_db_reale() {
//        // questo test bypassa l'API e parla direttamente col DB
//        // verifica che JPA stia davvero scrivendo su PostgreSQL
//        var count = orderRepository.count();
//        assertThat(count).isGreaterThan(0);
//    }
}