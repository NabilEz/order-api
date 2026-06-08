package com.nabil.order_api.security.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 8, message = "Password minimo 8 caratteri") String password,
        String role // opzionale, default USER
) {}
