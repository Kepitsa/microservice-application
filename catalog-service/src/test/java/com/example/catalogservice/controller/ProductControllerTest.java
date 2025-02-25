package com.example.catalogservice.controller;

import com.example.catalogservice.model.Product;
import com.example.catalogservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class ProductControllerTest {

    @InjectMocks
    private ProductController productController;

    @Mock
    private ProductRepository productRepository;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetAllProducts() {
        Product product = new Product("Товар 1", "Описание", new BigDecimal("100.00"), 10);
        product.setId(1L);
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<Product> products = productController.getAllProducts();

        assertEquals(1, products.size());
        assertEquals(1L, products.get(0).getId());
        assertEquals("Товар 1", products.get(0).getName());
        assertEquals(new BigDecimal("100.00"), products.get(0).getPrice());
        assertEquals(10, products.get(0).getStock());
    }
    @Test
    public void testGetProductById() {
        Product product = new Product("Товар 2", "Описание 2", new BigDecimal("150.00"), 5);
        product.setId(2L);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        ResponseEntity<Product> response = productController.getProductById(2L);

        assertEquals(200, response.getStatusCodeValue());
        Product result = response.getBody();
        assertEquals(2L, result.getId());
        assertEquals("Товар 2", result.getName());
        assertEquals(new BigDecimal("150.00"), result.getPrice());
        assertEquals(5, result.getStock());
    }

    @Test
    public void testGetProductByIdNotFound() {
        when(productRepository.findById(3L)).thenReturn(Optional.empty());

        ResponseEntity<Product> response = productController.getProductById(3L);

        assertEquals(404, response.getStatusCodeValue());
    }
}