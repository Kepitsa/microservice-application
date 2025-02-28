package com.example.deliveryservice.controller;

import com.example.deliveryservice.model.Delivery;
import com.example.deliveryservice.repository.DeliveryRepository;
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
    public ResponseEntity<Delivery> createDelivery(@PathVariable Long orderId, @RequestBody DeliveryRequest request) {
        String orderUrl = "http://localhost:8082/orders/" + orderId;
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
    public ResponseEntity<Delivery> trackDelivery(@PathVariable Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{deliveryId}/status")
    public ResponseEntity<Delivery> updateDeliveryStatus(@PathVariable Long deliveryId, @RequestBody StatusUpdateRequest request) {
        Optional<Delivery> deliveryOpt = deliveryRepository.findById(deliveryId);
        if (deliveryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Delivery delivery = deliveryOpt.get();
        delivery.setStatus(request.getStatus());
        deliveryRepository.save(delivery);

        // Обновление статуса заказа, если доставка завершена
        if ("DELIVERED".equals(request.getStatus())) {
            String orderUrl = "http://localhost:8082/orders/" + delivery.getOrderId();
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