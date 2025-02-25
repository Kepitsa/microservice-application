package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class OrderControllerTest {

    @InjectMocks
    private OrderController orderController;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCreateOrder() {
        when(restTemplate.getForObject("http://localhost:8081/cart/1/total", BigDecimal.class))
                .thenReturn(new BigDecimal("200.00"));

        OrderController.CartItem cartItem = new OrderController.CartItem();
        cartItem.setId(1L);
        cartItem.setCartId(1L);
        cartItem.setProductId(1L);
        cartItem.setQuantity(2);
        when(restTemplate.getForObject("http://localhost:8081/cart/1/items", OrderController.CartItem[].class))
                .thenReturn(new OrderController.CartItem[]{cartItem});

        OrderController.Product product = new OrderController.Product();
        product.setId(1L);
        product.setName("Товар 1");
        product.setDescription("Описание");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(10);
        when(restTemplate.getForObject("http://localhost:8080/products/1", OrderController.Product.class))
                .thenReturn(product);

        Order savedOrder = new Order(1L, new BigDecimal("200.00"), "CREATED");
        savedOrder.setId(1L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        ResponseEntity<Order> response = orderController.createOrder(1L);

        System.out.println("Response status: " + response.getStatusCode());
        response.getBody();
        System.out.println("Response body: " + response.getBody().getId() + ", " + response.getBody().getStatus());

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals(1L, response.getBody().getId());
        assertEquals("CREATED", response.getBody().getStatus());
    }
}