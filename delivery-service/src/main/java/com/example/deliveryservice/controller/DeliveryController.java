package com.example.deliveryservice.controller;

import com.example.deliveryservice.model.Delivery;
import com.example.deliveryservice.repository.DeliveryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private final DeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public DeliveryController(DeliveryRepository deliveryRepository, RestTemplate restTemplate) {
        this.deliveryRepository = deliveryRepository;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/{orderId}")
    @Operation(summary = "Создать доставку", description = "Создаёт новую доставку для оплаченного заказа с указанием службы доставки.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Доставка успешно создана",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Delivery.class))),
            @ApiResponse(responseCode = "400", description = "Заказ не оплачен (статус не PAID)")
    })
    public ResponseEntity<Delivery> createDelivery(
            @Parameter(description = "Идентификатор заказа", required = true) @PathVariable Long orderId,
            @Parameter(description = "Данные о службе доставки", required = true) @RequestBody DeliveryRequest request) {
        String orderUrl = "http://order-service:8082/orders/" + orderId; // Обновлено для Docker
        Order order = restTemplate.getForObject(orderUrl, Order.class);

        if (!"PAID".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(null);
        }

        BigDecimal cost = calculateDeliveryCost(order.getTotal(), request.getDeliveryService());
        Delivery delivery = new Delivery(orderId, request.getDeliveryService(), cost, "PENDING");
        Delivery savedDelivery = deliveryRepository.save(delivery);
        return ResponseEntity.ok(savedDelivery);
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Отследить доставку", description = "Возвращает текущий статус и данные доставки по её идентификатору.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о доставке получена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Delivery.class))),
            @ApiResponse(responseCode = "404", description = "Доставка с указанным ID не найдена")
    })
    public ResponseEntity<Delivery> trackDelivery(
            @Parameter(description = "Идентификатор доставки", required = true) @PathVariable Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{deliveryId}/status")
    @Operation(summary = "Обновить статус доставки", description = "Изменяет статус доставки. Если статус становится DELIVERED, обновляет статус соответствующего заказа.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус доставки успешно обновлён",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Delivery.class))),
            @ApiResponse(responseCode = "404", description = "Доставка с указанным ID не найдена")
    })
    public ResponseEntity<Delivery> updateDeliveryStatus(
            @Parameter(description = "Идентификатор доставки", required = true) @PathVariable Long deliveryId,
            @Parameter(description = "Новый статус доставки (например, PENDING, IN_TRANSIT, DELIVERED)", required = true) @RequestBody StatusUpdateRequest request) {
        Optional<Delivery> deliveryOpt = deliveryRepository.findById(deliveryId);
        if (deliveryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Delivery delivery = deliveryOpt.get();
        delivery.setStatus(request.getStatus());
        deliveryRepository.save(delivery);

        // Обновление статуса заказа, если доставка завершена
        if ("DELIVERED".equals(request.getStatus())) {
            String orderUrl = "http://order-service:8082/orders/" + delivery.getOrderId(); // Обновлено для Docker
            Order order = restTemplate.getForObject(orderUrl, Order.class);
            order.setStatus("DELIVERED");
            restTemplate.put(orderUrl, order);
            System.out.println("Заказ #" + delivery.getOrderId() + " доставлен");
        }

        return ResponseEntity.ok(delivery);
    }

    private BigDecimal calculateDeliveryCost(BigDecimal orderTotal, String deliveryService) {
        if ("Militech".equals(deliveryService)) {
            return orderTotal.multiply(BigDecimal.valueOf(0.1));
        } else if ("KangTao".equals(deliveryService)) {
            return orderTotal.multiply(BigDecimal.valueOf(0.15));
        }
        return BigDecimal.valueOf(50.00);
    }
}

class Order {
    private Long id;
    private Long userId;
    private BigDecimal total;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

class DeliveryRequest {
    private String deliveryService;

    public String getDeliveryService() {
        return deliveryService;
    }

    public void setDeliveryService(String deliveryService) {
        this.deliveryService = deliveryService;
    }
}

class StatusUpdateRequest {
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}