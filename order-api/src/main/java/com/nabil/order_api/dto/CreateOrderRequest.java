package com.nabil.order_api.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank(message = "customerId obbligatorio")
        String customerId,

        @NotBlank(message = "productId obbligatorio")
        String productId,

        @Positive(message = "importo deve essere positivo")
        BigDecimal totalAmount
) {}
