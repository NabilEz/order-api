package com.nabil.order_api.service;


import com.nabil.order_api.domain.Order;
import com.nabil.order_api.domain.OrderRepository;
import com.nabil.order_api.domain.OrderStatus;
import com.nabil.order_api.dto.CreateOrderRequest;
import com.nabil.order_api.dto.OrderResponse;
import com.nabil.order_api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest req) {
        var order = new Order(req.customerId(), req.productId(), req.totalAmount());
        var saved = repository.save(order);
        return toResponse(saved, new OrderStatus.Pending(saved.getCreatedAt()));
    }

    public List<OrderResponse> findAll() {
        return repository.findAll().stream()
                .map(o -> toResponse(o, new OrderStatus.Pending(o.getCreatedAt())))
                .toList();
    }

    public OrderResponse findById(Long id) {
        return repository.findById(id)
                .map(o -> toResponse(o, new OrderStatus.Pending(o.getCreatedAt())))
                .orElseThrow(() -> new ResourceNotFoundException("Ordine", id));    }

    @Transactional
    public OrderResponse ship(Long id, String courier, String trackingCode) {
        var order = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ordine", id));
        order.setStatusType(Order.StatusType.SHIPPED);
        var status = new OrderStatus.Shipped(
                courier, trackingCode,
                java.time.LocalDateTime.now().plusDays(3)
        );
        return toResponse(repository.save(order), status);
    }

    @Transactional
    public OrderResponse cancel(Long id, String reason) {
        var order = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ordine", id));
        order.setStatusType(Order.StatusType.CANCELLED);
        var status = new OrderStatus.Cancelled(reason, java.time.LocalDateTime.now());
        return toResponse(repository.save(order), status);
    }

    // Qui entra in gioco il pattern matching su sealed class
    private OrderResponse toResponse(Order order, OrderStatus status) {
        String statusName = switch (status) {
            case OrderStatus.Pending p      -> "PENDING";
            case OrderStatus.Processing p   -> "PROCESSING";
            case OrderStatus.Shipped s      -> "SHIPPED";
            case OrderStatus.Cancelled c    -> "CANCELLED";
        };

        // Messaggio contestuale per ogni stato — nessun default, nessun NPE
        String message = switch (status) {
            case OrderStatus.Pending p ->
                    "In attesa di elaborazione dal " + p.createdAt().toLocalDate();
            case OrderStatus.Processing p ->
                    "In lavorazione nel magazzino " + p.warehouseId();
            case OrderStatus.Shipped s ->
                    "Spedito con " + s.courier() + " (tracking: " + s.trackingCode() +
                            "), arrivo previsto: " + s.eta().toLocalDate();
            case OrderStatus.Cancelled c ->
                    "Annullato il " + c.cancelledAt().toLocalDate() +
                            " — motivo: " + c.reason();
        };

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getTotalAmount(),
                statusName,
                message,
                order.getCreatedAt()
        );
    }
}