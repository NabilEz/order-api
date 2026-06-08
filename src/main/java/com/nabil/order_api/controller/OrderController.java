package com.nabil.order_api.controller;


import com.nabil.order_api.dto.CreateOrderRequest;
import com.nabil.order_api.dto.OrderResponse;
import com.nabil.order_api.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrderResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PatchMapping("/{id}/ship")
    public OrderResponse ship(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return service.ship(id, body.get("courier"), body.get("trackingCode"));
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return service.cancel(id, body.get("reason"));
    }
}
