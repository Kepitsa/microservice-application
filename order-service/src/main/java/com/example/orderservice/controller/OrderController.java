package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
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
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public OrderController(OrderRepository orderRepository, RestTemplate restTemplate) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Обновить заказ", description = "Обновляет статус существующего заказа по его идентификатору.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно обновлён",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "404", description = "Заказ с указанным ID не найден")
    })
    public ResponseEntity<Order> updateOrder(
            @Parameter(description = "Идентификатор заказа", required = true) @PathVariable Long orderId,
            @Parameter(description = "Обновлённые данные заказа (только статус используется)", required = true) @RequestBody Order updatedOrder) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    order.setStatus(updatedOrder.getStatus());
                    orderRepository.save(order);
                    return ResponseEntity.ok(order);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Создать заказ
    @PostMapping("/{userId}")
    @Operation(summary = "Создать новый заказ", description = "Создаёт заказ для пользователя на основе текущей корзины. Проверяет наличие товаров на складе.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно создан",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "400", description = "Корзина пуста, общая стоимость <= 0 или недостаточно товара на складе")
    })
    public ResponseEntity<Order> createOrder(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId) {
        String cartTotalUrl = "http://cart-service:8081/cart/" + userId + "/total";
        BigDecimal total = restTemplate.getForObject(cartTotalUrl, BigDecimal.class);

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }

        String cartItemsUrl = "http://cart-service:8081/cart/" + userId + "/items";
        CartItem[] items = restTemplate.getForObject(cartItemsUrl, CartItem[].class);

        if (items.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        for (CartItem item : items) {
            String productUrl = "http://catalog-service:8080/products/" + item.getProductId();
            Product product = restTemplate.getForObject(productUrl, Product.class);
            if (product.getStock() < item.getQuantity()) {
                System.out.println("Недостаточно товара " + item.getProductId() + " на складе");
                return ResponseEntity.status(400).body(null);
            }
        }

        Order order = new Order(userId, total, "CREATED");
        Order savedOrder = orderRepository.save(order);
        System.out.println("Заказ #" + savedOrder.getId() + " создан для пользователя " + userId);

        return ResponseEntity.ok(savedOrder);
    }

    // Получить заказ с деталями
    @GetMapping("/{orderId}")
    @Operation(summary = "Получить детали заказа", description = "Возвращает информацию о заказе, включая список товаров, по его идентификатору.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Детали заказа успешно получены",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDetails.class))),
            @ApiResponse(responseCode = "404", description = "Заказ с указанным ID не найден")
    })
    public ResponseEntity<OrderDetails> getOrderStatus(
            @Parameter(description = "Идентификатор заказа", required = true) @PathVariable Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    OrderDetails details = new OrderDetails(order);
                    String cartItemsUrl = "http://cart-service:8081/cart/" + order.getUserId() + "/items";
                    CartItem[] items = restTemplate.getForObject(cartItemsUrl, CartItem[].class);
                    for (CartItem item : items) {
                        String productUrl = "http://catalog-service:8080/products/" + item.getProductId();
                        Product product = restTemplate.getForObject(productUrl, Product.class);
                        details.addItem(new OrderItem(item.getProductId(), product.getName(), item.getQuantity()));
                    }
                    return ResponseEntity.ok(details);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Получить все заказы
    @GetMapping("/user/{userId}")
    @Operation(summary = "Получить все заказы пользователя", description = "Возвращает список всех заказов, связанных с указанным пользователем.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список заказов успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Order.class)))
    })
    public ResponseEntity<List<Order>> getOrdersByUser(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId) {
        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> order.getUserId().equals(userId))
                .toList();
        return ResponseEntity.ok(orders);
    }

    // Отменить заказ
    @DeleteMapping("/{orderId}")
    @Operation(summary = "Отменить заказ", description = "Отменяет заказ, возвращает товары на склад и уведомляет платёжный сервис, если заказ был оплачен.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно отменён",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Нельзя отменить заказ в статусе DELIVERED или другом неподходящем статусе"),
            @ApiResponse(responseCode = "404", description = "Заказ с указанным ID не найден")
    })
    public ResponseEntity<String> cancelOrder(
            @Parameter(description = "Идентификатор заказа", required = true) @PathVariable Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if ("CREATED".equals(order.getStatus()) || "PAID".equals(order.getStatus())) {
                        order.setStatus("CANCELLED");
                        orderRepository.save(order);

                        // Возвращаем stock для всех товаров в корзине
                        String cartItemsUrl = "http://cart-service:8081/cart/" + order.getUserId() + "/items";
                        CartItem[] items = restTemplate.getForObject(cartItemsUrl, CartItem[].class);
                        for (CartItem item : items) {
                            String stockUpdateUrl = "http://catalog-service:8080/products/" + item.getProductId() + "/stock";
                            StockUpdateRequest request = new StockUpdateRequest(-item.getQuantity());
                            restTemplate.put(stockUpdateUrl, request);
                        }

                        // Уведомляем PaymentService об отмене
                        String paymentUpdateUrl = "http://payment-service:8083/payments/order/" + orderId + "/cancel";
                        restTemplate.put(paymentUpdateUrl, null);

                        System.out.println("Заказ #" + orderId + " отменён");
                        return ResponseEntity.ok("Заказ отменён");
                    } else if ("DELIVERED".equals(order.getStatus())) {
                        return ResponseEntity.badRequest().body("Нельзя отменить доставленный заказ");
                    } else {
                        return ResponseEntity.badRequest().body("Нельзя отменить заказ в статусе " + order.getStatus());
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    static class OrderDetails {
        private Long id;
        private Long userId;
        private BigDecimal total;
        private String status;
        private List<OrderItem> items;

        public OrderDetails(Order order) {
            this.id = order.getId();
            this.userId = order.getUserId();
            this.total = order.getTotal();
            this.status = order.getStatus();
            this.items = new ArrayList<>();
        }

        public void addItem(OrderItem item) { this.items.add(item); }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<OrderItem> getItems() { return items; }
        public void setItems(List<OrderItem> items) { this.items = items; }
    }

    static class OrderItem {
        private Long productId;
        private String productName;
        private int quantity;

        public OrderItem(Long productId, String productName, int quantity) {
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

    static class Product {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private int stock;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }

    static class StockUpdateRequest {
        private int quantity;

        public StockUpdateRequest() {}
        public StockUpdateRequest(int quantity) { this.quantity = quantity; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}