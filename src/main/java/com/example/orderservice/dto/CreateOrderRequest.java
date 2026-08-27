package com.example.orderservice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    private String productCode;

    @Min(1)
    private Integer quantity;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal totalAmount;
}