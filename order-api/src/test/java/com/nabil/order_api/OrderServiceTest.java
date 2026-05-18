//package com.nabil.order_api;
//
//
//import com.nabil.order_api.service.OrderService;
//import com.nabil.order_api.domain.Order;
//import com.nabil.order_api.domain.OrderRepository;
//import com.nabil.order_api.dto.CreateOrderRequest;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.math.BigDecimal;
//import java.util.Optional;
//
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class OrderServiceTest {
//
//    @Mock OrderRepository repository;
//    @InjectMocks
//    OrderService service;
//
//    @Test
//    void crea_ordine_con_stato_pending() {
//        var req = new CreateOrderRequest("cust-1", "prod-42", new BigDecimal("99.90"));
//        var savedOrder = new Order("cust-1", "prod-42", new BigDecimal("99.90"));
//
//        when(repository.save(any())).thenReturn(savedOrder);
//
//        var response = service.create(req);
//
//
//
//        assertThat(response.status()).isEqualTo("PENDING");
//        assertThat(response.customerId()).isEqualTo("cust-1");
//        assertThat(response.statusMessage()).contains("In attesa");
//    }
//
//    @Test
//    void cancella_ordine_include_motivo_nel_messaggio() {
//        var order = new Order("cust-1", "prod-42", new BigDecimal("99.90"));
//        when(repository.findById(1L)).thenReturn(Optional.of(order));
//        when(repository.save(any())).thenReturn(order);
//
//        var response = service.cancel(1L, "prodotto esaurito");
//
//        assertThat(response.status()).isEqualTo("CANCELLED");
//        assertThat(response.statusMessage()).contains("prodotto esaurito");
//    }
//}