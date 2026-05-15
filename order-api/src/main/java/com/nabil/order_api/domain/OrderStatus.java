package com.nabil.order_api.domain;


import java.time.LocalDateTime;

public sealed interface OrderStatus
        permits OrderStatus.Pending,
        OrderStatus.Processing,
        OrderStatus.Shipped,
        OrderStatus.Cancelled {
//todo njbhj
    record Pending(LocalDateTime createdAt)
            implements OrderStatus {}

    record Processing(String warehouseId)
            implements OrderStatus {}

    record Shipped(String courier, String trackingCode, LocalDateTime eta)
            implements OrderStatus {}

    record Cancelled(String reason, LocalDateTime cancelledAt)
            implements OrderStatus {}
}