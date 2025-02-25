package com.example.paymentservice.controller;

import com.example.paymentservice.model.Payment;
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
public class PaymentIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("payment_db")
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
        restTemplate.postForEntity("http://localhost:8080/products", product, Void.class);

        CartItemRequest cartRequest = new CartItemRequest(1L, 2);
        restTemplate.postForEntity("http://localhost:8081/cart/1/add", cartRequest, Void.class);

        ResponseEntity<Order> orderResponse = restTemplate.postForEntity("http://localhost:8082/orders/1", null, Order.class);
        assertEquals(HttpStatus.OK, orderResponse.getStatusCode());
    }

    @Test
    public void testProcessPayment_success() {
        ResponseEntity<Order> orderResponse = restTemplate.postForEntity("http://localhost:8082/orders/1", null, Order.class);
        Long orderId = orderResponse.getBody().getId();

        ResponseEntity<Payment> response = restTemplate.postForEntity("/payments/" + orderId, null, Payment.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Payment payment = response.getBody();
        assertNotNull(payment);
        assertEquals(orderId, payment.getOrderId());
        assertNotNull(payment.getStatus());
    }

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
}