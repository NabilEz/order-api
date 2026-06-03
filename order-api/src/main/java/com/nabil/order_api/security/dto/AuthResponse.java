package com.nabil.order_api.security.dto;


public record AuthResponse(
        String accessToken,
        String refreshToken,
        String email,
        String role,
        long expiresIn // secondi
) {}