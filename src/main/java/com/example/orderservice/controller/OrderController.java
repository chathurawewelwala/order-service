package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role-based authorization:
 * <ul>
 *   <li>ORDER_WRITE – create orders (POST)</li>
 *   <li>ORDER_READ  – list / get orders (GET)</li>
 * </ul>
 * Assign these as realm roles in Keycloak.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Only users with ROLE_ORDER_WRITE can create orders */
    @PostMapping
    @PreAuthorize("hasRole('ORDER_WRITE')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        orderService.processOrderAsync(response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Users with ROLE_ORDER_READ can fetch a single order */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORDER_READ')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /** Users with ROLE_ORDER_READ can list orders by customer */
    @GetMapping
    @PreAuthorize("hasRole('ORDER_READ')")
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(
            @RequestParam String customerId) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId));
    }
}
