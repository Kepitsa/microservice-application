//Может кончиться товар или в корзине не будет товара или оплата не прошла = тогда тест не пройдёт
package com.example.deliveryservice.controller;

import com.example.deliveryservice.model.Delivery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class DeliveryIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("delivery_db")
            .withUsername("postgres")
            .withPassword("admin");

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @BeforeEach
    public void setUp() {
        Product product = new Product("Test Item", "Description", new BigDecimal("50.00"), 10);
        ResponseEntity<Void> catalogResponse = restTemplate.postForEntity("http://localhost:8080/products", product, Void.class);
        assertEquals(HttpStatus.OK, catalogResponse.getStatusCode(), "Failed to add product to catalog");

        CartItemRequest cartRequest = new CartItemRequest(1L, 2);
        ResponseEntity<Void> cartResponse = restTemplate.postForEntity("http://localhost:8081/cart/1/add", cartRequest, Void.class);
        assertEquals(HttpStatus.OK, cartResponse.getStatusCode(), "Failed to add item to cart");
    }

    @Test
    public void testCreateDelivery_success() {
        ResponseEntity<Order> orderResponse = restTemplate.postForEntity("http://localhost:8082/orders/1", null, Order.class);
        assertEquals(HttpStatus.OK, orderResponse.getStatusCode(), "Failed to create order: " + orderResponse.getStatusCode());
        Long orderId = orderResponse.getBody().getId();

        ResponseEntity<Payment> paymentResponse = restTemplate.postForEntity("http://localhost:8083/payments/" + orderId, null, Payment.class);
        assertEquals(HttpStatus.OK, paymentResponse.getStatusCode(), "Failed to process payment");

        if ("COMPLETED".equals(paymentResponse.getBody().getStatus())) {
            DeliveryRequest request = new DeliveryRequest();
            request.setDeliveryService("Militech");
            ResponseEntity<Delivery> response = restTemplate.postForEntity("/delivery/" + orderId, request, Delivery.class);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "Failed to create delivery");
            Delivery delivery = response.getBody();
            assertNotNull(delivery);
            assertEquals(orderId, delivery.getOrderId());
            assertEquals("Militech", delivery.getDeliveryService());
            assertEquals("PENDING", delivery.getStatus());
        } else {
            System.out.println("Payment failed with status: " + paymentResponse.getBody().getStatus() + ", skipping delivery test");
        }
    }

    // Вспомогательные классы
    static class Product {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private int stock;

        public Product(String name, String description, BigDecimal price, int stock) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.stock = stock;
        }

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

    static class CartItemRequest {
        private Long productId;
        private int quantity;

        public CartItemRequest(Long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    static class Order {
        private Long id;
        private Long userId;
        private BigDecimal total;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    static class Payment {
        private Long id;
        private Long orderId;
        private BigDecimal amount;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}