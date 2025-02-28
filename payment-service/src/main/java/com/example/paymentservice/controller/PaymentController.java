package com.example.paymentservice.controller;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentController(PaymentRepository paymentRepository, RestTemplate restTemplate) {
        this.paymentRepository = paymentRepository;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/{orderId}")
    @Operation(summary = "Обработать платёж", description = "Обрабатывает платёж для указанного заказа. В случае успеха обновляет статус заказа на PAID и уменьшает запас товаров на складе.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Платёж обработан (статус COMPLETED или FAILED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Payment.class))),
            @ApiResponse(responseCode = "400", description = "Заказ не в статусе CREATED")
    })
    public ResponseEntity<Payment> processPayment(
            @Parameter(description = "Идентификатор заказа", required = true) @PathVariable Long orderId) {
        String orderUrl = "http://order-service:8082/orders/" + orderId;
        Order order = restTemplate.getForObject(orderUrl, Order.class);

        if (!"CREATED".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(null);
        }

        Payment payment = new Payment(orderId, order.getTotal(), "PENDING");
        Payment savedPayment = paymentRepository.save(payment);

        boolean paymentSuccess = simulatePayment();
        if (paymentSuccess) {
            savedPayment.setStatus("COMPLETED");
            paymentRepository.save(savedPayment);
            updateOrderStatus(orderId, "PAID");

            String cartItemsUrl = "http://cart-service:8081/cart/" + order.getUserId() + "/items";
            CartItem[] items = restTemplate.getForObject(cartItemsUrl, CartItem[].class);
            for (CartItem item : items) {
                String stockUpdateUrl = "http://catalog-service:8080/products/" + item.getProductId() + "/stock";
                StockUpdateRequest request = new StockUpdateRequest(item.getQuantity());
                restTemplate.put(stockUpdateUrl, request);
            }

            System.out.println("Платёж для заказа #" + orderId + " успешно обработан");
        } else {
            savedPayment.setStatus("FAILED");
            paymentRepository.save(savedPayment);
            System.out.println("Ошибка обработки платежа для заказа #" + orderId);
        }

        return ResponseEntity.ok(savedPayment);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Получить статус платежа", description = "Возвращает детализированную информацию о платеже, включая статус заказа и список товаров.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о платеже успешно получена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentDetails.class))),
            @ApiResponse(responseCode = "404", description = "Платёж с указанным ID не найден")
    })
    public ResponseEntity<PaymentDetails> getPaymentStatus(
            @Parameter(description = "Идентификатор платежа", required = true) @PathVariable Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(payment -> {
                    String orderUrl = "http://order-service:8082/orders/" + payment.getOrderId();
                    Order order = restTemplate.getForObject(orderUrl, Order.class);
                    PaymentDetails details = new PaymentDetails(payment);
                    details.setOrderStatus(order.getStatus());

                    String cartItemsUrl = "http://cart-service:8081/cart/" + order.getUserId() + "/items";
                    CartItem[] items = restTemplate.getForObject(cartItemsUrl, CartItem[].class);
                    for (CartItem item : items) {
                        String productUrl = "http://catalog-service:8080/products/" + item.getProductId();
                        Product product = restTemplate.getForObject(productUrl, Product.class);
                        details.addItem(new PaymentItem(item.getProductId(), product.getName(), item.getQuantity()));
                    }
                    return ResponseEntity.ok(details);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/order/{orderId}/cancel")
    @Operation(summary = "Отменить платёж", description = "Отменяет платёж для указанного заказа, переводя его в статус REFUNDED, если он был в статусе COMPLETED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Платёж успешно отменён или не найден подходящий платёж для отмены"),
            @ApiResponse(responseCode = "404", description = "Заказ не найден (не проверяется напрямую, но может быть причиной отсутствия изменений)")
    })
    public ResponseEntity<Void> cancelPayment(
            @Parameter(description = "Идентификатор заказа", required = true) @PathVariable Long orderId) {
        paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrderId().equals(orderId) && "COMPLETED".equals(payment.getStatus()))
                .forEach(payment -> {
                    payment.setStatus("REFUNDED");
                    paymentRepository.save(payment);
                    System.out.println("Платёж для заказа #" + orderId + " возвращён");
                });
        return ResponseEntity.ok().build();
    }

    boolean simulatePayment() {
        return new Random().nextBoolean();
    }

    private void updateOrderStatus(Long orderId, String status) {
        String orderUrl = "http://order-service:8082/orders/" + orderId;
        Order order = restTemplate.getForObject(orderUrl, Order.class);
        order.setStatus(status);
        restTemplate.put(orderUrl, order);
    }

    static class Order {
        private Long id;
        private Long userId;
        private java.math.BigDecimal total;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public java.math.BigDecimal getTotal() { return total; }
        public void setTotal(java.math.BigDecimal total) { this.total = total; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    static class CartItem {
        private Long id;
        private Long cartId;
        private Long productId;
        private int quantity;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCartId() { return cartId; }
        public void setCartId(Long cartId) { this.cartId = cartId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    static class StockUpdateRequest {
        private int quantity;

        public StockUpdateRequest() {}
        public StockUpdateRequest(int quantity) { this.quantity = quantity; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    static class Product {
        private Long id;
        private String name;
        private String description;
        private java.math.BigDecimal price;
        private int stock;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }

    static class PaymentDetails {
        private Long id;
        private Long orderId;
        private java.math.BigDecimal amount;
        private String status;
        private String orderStatus;
        private List<PaymentItem> items;

        public PaymentDetails(Payment payment) {
            this.id = payment.getId();
            this.orderId = payment.getOrderId();
            this.amount = payment.getAmount();
            this.status = payment.getStatus();
            this.items = new ArrayList<>();
        }

        public void addItem(PaymentItem item) {
            this.items.add(item);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOrderStatus() { return orderStatus; }
        public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
        public List<PaymentItem> getItems() { return items; }
        public void setItems(List<PaymentItem> items) { this.items = items; }
    }

    static class PaymentItem {
        private Long productId;
        private String productName;
        private int quantity;

        public PaymentItem(Long productId, String productName, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}