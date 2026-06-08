package com.nabil.order_api.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    // Memorizziamo lo stato come stringa + campi extra
    // In un progetto reale si usa @Convert con un AttributeConverter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType statusType = StatusType.PENDING;

    private String statusDetail; // JSON o stringa per i campi extra

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum StatusType { PENDING, PROCESSING, SHIPPED, CANCELLED }

    // costruttore, getter, setter...
    protected Order() {}

    public Order(String customerId, String productId, BigDecimal totalAmount) {
        this.customerId = customerId;
        this.productId = productId;
        this.totalAmount = totalAmount;
    }

    // getter omessi per brevità — in produzione usa Lombok o records per DTO
    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public StatusType getStatusType() { return statusType; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStatusType(StatusType statusType) {
        this.statusType = statusType;
    }
}
