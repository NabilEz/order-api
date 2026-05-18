# order-api — cosa ho imparato

Progetto didattico: REST API per gestione ordini in Java 21 + Spring Boot 3.

---

## Le tre feature Java 21 usate

### Records
Classi immutabili di dati in una riga. Usati come DTO di input e output:

```java
public record CreateOrderRequest(
    @NotBlank String customerId,
    @NotBlank String productId,
    @Positive BigDecimal totalAmount
) {}
```

Il compilatore genera automaticamente costruttore, getter, `equals`, `hashCode`, `toString`.
Nessun Lombok necessario. Immutabili per default → thread-safe.

### Sealed classes + pattern matching
Modellano un insieme chiuso di varianti di un tipo. Ogni stato porta solo i dati che ha senso che abbia:

```java
public sealed interface OrderStatus
        permits OrderStatus.Pending, OrderStatus.Processing,
                OrderStatus.Shipped, OrderStatus.Cancelled {

    record Pending(LocalDateTime createdAt)      implements OrderStatus {}
    record Shipped(String courier, String tracking, LocalDateTime eta) implements OrderStatus {}
    record Cancelled(String reason, LocalDateTime cancelledAt) implements OrderStatus {}
}
```

Combinati con switch esaustivo: se aggiungi uno stato e dimentichi un case → errore a compile time, non a runtime.

### Virtual threads
Una riga in `application.properties`, scalabilità quasi reattiva senza riscrivere nulla:

```properties
spring.threads.virtual.enabled=true
```

Adatto a workload I/O bound (chiamate DB, HTTP). Non usare per CPU bound.

---

## Spring Boot — concetti applicati

### `@Transactional`
Garantisce atomicità su operazioni multiple al DB. Senza di esso, un crash a metà lascia dati inconsistenti.

```java
@Transactional          // apre transazione, commit a fine metodo, rollback su eccezione
public OrderResponse cancel(Long id, String reason) {
    var order = repository.findById(id)...   // op 1
    order.setStatusType(CANCELLED);
    return toResponse(repository.save(order), status);  // op 2 — atomica con op 1
}
```

`readOnly = true` a livello di classe ottimizza le query di sola lettura (no dirty checking).

### Spring Data JPA
Nessuna query scritta a mano per i casi comuni. Spring genera tutto da interfaccia:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(String customerId);
    List<Order> findByStatusType(Order.StatusType statusType);
}
```

### Bean Validation
Validazione dichiarativa sui record di input con `spring-boot-starter-validation`:

```java
public record CreateOrderRequest(
    @NotBlank(message = "customerId obbligatorio") String customerId,
    @Positive(message = "importo deve essere positivo") BigDecimal totalAmount
) {}
```

Attivata sul controller con `@Valid @RequestBody`.

---

## Struttura del progetto

```
order-api/
├── domain/
│   ├── OrderStatus.java      # sealed interface + record stati
│   ├── Order.java            # entity JPA
│   └── OrderRepository.java  # Spring Data
├── dto/
│   ├── CreateOrderRequest.java  # record input
│   └── OrderResponse.java       # record output
├── service/
│   └── OrderService.java     # logica, @Transactional
├── web/
│   └── OrderController.java  # REST endpoints
├── Dockerfile                # multi-stage build, JRE 21
├── docker-compose.yml        # app + postgres
└── application.properties
```

---

## Endpoints

| Metodo | Path | Descrizione |
|---|---|---|
| GET | `/api/orders` | Lista tutti gli ordini |
| GET | `/api/orders/{id}` | Singolo ordine |
| POST | `/api/orders` | Crea ordine (stato: PENDING) |
| PATCH | `/api/orders/{id}/ship` | Transizione → SHIPPED |
| PATCH | `/api/orders/{id}/cancel` | Transizione → CANCELLED |

---

## Prossimi passi (Fase 2)

- [ ] Spring Security con JWT
- [ ] Global exception handler con `@ControllerAdvice`
- [ ] Test con Testcontainers (PostgreSQL reale nei test)
- [ ] CI/CD con GitHub Actions
- [ ] Deploy su Railway o Fly.io