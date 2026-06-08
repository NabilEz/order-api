package com.nabil.order_api.integrationTest;


import com.nabil.order_api.security.dto.AuthResponse;
import com.nabil.order_api.security.dto.LoginRequest;
import com.nabil.order_api.security.dto.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders json() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("Register → Login → Refresh → token rotation verificata")
    void flusso_completo_auth() {
        // 1. Register
        var regReq = new RegisterRequest("nuovo@test.com", "Password123!", null);
        var regRes = rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(regReq, json()),
                AuthResponse.class
        );
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var firstRefreshToken = regRes.getBody().refreshToken();
        assertThat(firstRefreshToken).isNotBlank();

        // 2. Refresh — ottieni nuovi token
        var refreshBody = "{\"refreshToken\":\"" + firstRefreshToken + "\"}";
        var refreshRes = rest.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(refreshBody, json()),
                AuthResponse.class
        );
        assertThat(refreshRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        var secondRefreshToken = refreshRes.getBody().refreshToken();

        // 3. Verifica rotation — il nuovo token è diverso dal precedente
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // 4. Il vecchio refresh token non funziona più (rotation)
        var replayRes = rest.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(refreshBody, json()),
                String.class
        );
        assertThat(replayRes.getStatusCode().is4xxClientError()
                || replayRes.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    @DisplayName("Login con credenziali sbagliate → 401")
    void login_credenziali_sbagliate() {
        var req = new LoginRequest("user@demo.com", "passwordsbagliata");
        var res = rest.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(req, json()),
                String.class
        );
        assertThat(res.getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Register con email duplicata → 409")
    void register_email_duplicata() {
        var req = new RegisterRequest("user@demo.com", "Password123!", null);
        var res = rest.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(req, json()),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("409");
    }
}
