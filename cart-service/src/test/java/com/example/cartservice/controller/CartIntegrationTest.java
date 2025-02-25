package com.example.cartservice.controller;

import com.example.cartservice.model.Cart;
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
public class CartIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("cart_db")
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
        // Подготовка: добавляем товар в catalog-service
        Product product = new Product("Test Item", "Description", new BigDecimal("50.00"), 10);
        restTemplate.postForEntity("http://localhost:8080/products", product, Void.class);
    }

    @Test
    public void testAddToCart_success() {
        CartItemRequest request = new CartItemRequest(1L, 2);
        ResponseEntity<Cart> response = restTemplate.postForEntity("/cart/1/add", request, Cart.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Cart cart = response.getBody();
        assertNotNull(cart);
        assertEquals(1L, cart.getUserId());
        assertNotNull(cart.getId());
    }
}