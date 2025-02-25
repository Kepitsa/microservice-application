package com.example.catalogservice.controller;

import com.example.catalogservice.model.Product;
import org.junit.jupiter.api.BeforeAll;
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
public class CatalogIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("catalog_db")
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

    @Test
    public void testCreateProduct_success() {
        Product newProduct = new Product("Test Item", "Description", new BigDecimal("99.99"), 5);
        ResponseEntity<Product> response = restTemplate.postForEntity("/products", newProduct, Product.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Product createdProduct = response.getBody();
        assertNotNull(createdProduct);
        assertNotNull(createdProduct.getId());
        assertEquals("Test Item", createdProduct.getName());
        assertEquals(new BigDecimal("99.99"), createdProduct.getPrice());
        assertEquals(5, createdProduct.getStock());
    }
}