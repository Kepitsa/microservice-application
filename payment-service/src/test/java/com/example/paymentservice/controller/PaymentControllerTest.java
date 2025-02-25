package com.example.paymentservice.controller;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
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
import static org.mockito.Mockito.*;

public class PaymentControllerTest {

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testProcessPayment_success() {
        PaymentController.Order order = new PaymentController.Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setTotal(new BigDecimal("200.00"));
        order.setStatus("CREATED");
        when(restTemplate.getForObject("http://localhost:8082/orders/1", PaymentController.Order.class))
                .thenReturn(order);

        PaymentController.CartItem cartItem = new PaymentController.CartItem();
        cartItem.setId(1L);
        cartItem.setCartId(1L);
        cartItem.setProductId(1L);
        cartItem.setQuantity(2);
        when(restTemplate.getForObject("http://localhost:8081/cart/1/items", PaymentController.CartItem[].class))
                .thenReturn(new PaymentController.CartItem[]{cartItem});

        Payment pendingPayment = new Payment(1L, new BigDecimal("200.00"), "PENDING");
        pendingPayment.setId(1L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(pendingPayment);

        doNothing().when(restTemplate).put(eq("http://localhost:8082/orders/1"), any(PaymentController.Order.class));

        PaymentController spyController = spy(paymentController);
        doReturn(true).when(spyController).simulatePayment();

        ResponseEntity<Payment> response = spyController.processPayment(1L);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals("COMPLETED", response.getBody().getStatus());
        assertEquals(1L, response.getBody().getId());
        assertEquals(new BigDecimal("200.00"), response.getBody().getAmount());
    }
}