package com.nabil.order_api.dto;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String customerId,
        String productId,
        BigDecimal totalAmount,
        String status,
        String statusMessage,
        LocalDateTime createdAt
) {}