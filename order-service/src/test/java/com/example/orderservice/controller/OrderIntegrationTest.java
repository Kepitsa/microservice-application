package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
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
public class OrderIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("order_db")
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
        restTemplate.postForEntity("http://localhost:8081/cart/1/add",
                new CartItemRequest(1L, 2),
                Void.class);
    }

    @Test
    public void testCreateOrder_success() {
        ResponseEntity<Order> response = restTemplate.postForEntity("/orders/1", null, Order.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Order createdOrder = response.getBody();
        assertNotNull(createdOrder);
        assertEquals(1L, createdOrder.getUserId());
        assertEquals("CREATED", createdOrder.getStatus());
        assertNotNull(createdOrder.getTotal());
    }
}

// Вспомогательный класс для запроса в cart-service
class CartItemRequest {
    private Long productId;
    private int quantity;

    public CartItemRequest(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}