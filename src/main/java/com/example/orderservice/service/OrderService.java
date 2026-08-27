package com.example.orderservice.service;

import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer={}", request.getCustomerId());

        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productCode(request.getProductCode())
                .quantity(request.getQuantity())
                .totalAmount(request.getTotalAmount())
                .status(OrderStatus.CREATED)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created with id={}", saved.getId());
        return toResponse(saved);
    }

    @Cacheable(value = "orders", key = "#id")
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        log.info(">>> CACHE MISS - Fetching order id={} from PostgreSQL", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        log.info("<<< Loaded order id={} from PostgreSQL", id);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Example of async / concurrent work (e.g. sending notification)
    @Async("orderTaskExecutor")
    public void processOrderAsync(Long orderId) {
        String threadName = Thread.currentThread().getName();

        log.info("🚀 [ASYNC START] orderId={} | Running on thread: {}", orderId, threadName);

        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Async task interrupted for orderId={}", orderId);
        }

        log.info("✅ [ASYNC FINISHED] orderId={} | Thread: {}", orderId, threadName);

    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .productCode(order.getProductCode())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}