package com.example.deliveryservice.controller;

import com.example.deliveryservice.model.Delivery;
import com.example.deliveryservice.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private DeliveryController deliveryController;

    private Order paidOrder;
    private Order unpaidOrder;

    @BeforeEach
    void setUp() {
        paidOrder = new Order();
        paidOrder.setId(1L);
        paidOrder.setStatus("PAID");
        paidOrder.setTotal(BigDecimal.valueOf(1000));

        unpaidOrder = new Order();
        unpaidOrder.setId(2L);
        unpaidOrder.setStatus("PENDING");
        unpaidOrder.setTotal(BigDecimal.valueOf(1000));
    }

    @Test
    void createDelivery_successfulCreation_returnsOk() {
        DeliveryRequest request = new DeliveryRequest();
        request.setDeliveryService("Militech");
        when(restTemplate.getForObject(anyString(), eq(Order.class))).thenReturn(paidOrder);
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(invocation -> {
            Delivery delivery = invocation.getArgument(0);
            delivery.setId(1L);
            return delivery;
        });

        ResponseEntity<Delivery> response = deliveryController.createDelivery(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getOrderId());
        assertEquals("Militech", response.getBody().getDeliveryService());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(response.getBody().getCost())); // Изменено
        assertEquals("PENDING", response.getBody().getStatus());
    }

    @Test
    void createDelivery_unpaidOrder_returnsBadRequest() {
        DeliveryRequest request = new DeliveryRequest();
        request.setDeliveryService("Militech");
        when(restTemplate.getForObject(anyString(), eq(Order.class))).thenReturn(unpaidOrder);

        ResponseEntity<Delivery> response = deliveryController.createDelivery(2L, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void trackDelivery_existingDelivery_returnsOk() {
        // Arrange
        Delivery delivery = new Delivery(1L, "KangTao", BigDecimal.valueOf(150), "PENDING");
        delivery.setId(1L);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        ResponseEntity<Delivery> response = deliveryController.trackDelivery(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(delivery, response.getBody());
    }

    @Test
    void trackDelivery_nonExistingDelivery_returnsNotFound() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.empty());
        ResponseEntity<Delivery> response = deliveryController.trackDelivery(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void updateDeliveryStatus_toDelivered_updatesOrderStatus() {
        Delivery delivery = new Delivery(1L, "Militech", BigDecimal.valueOf(100), "PENDING");
        delivery.setId(1L);
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus("DELIVERED");

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(restTemplate.getForObject(anyString(), eq(Order.class))).thenReturn(paidOrder);
        when(deliveryRepository.save(any(Delivery.class))).thenReturn(delivery);

        ResponseEntity<Delivery> response = deliveryController.updateDeliveryStatus(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DELIVERED", response.getBody().getStatus());
        verify(restTemplate).put(anyString(), any(Order.class));
    }

    @Test
    void updateDeliveryStatus_nonExistingDelivery_returnsNotFound() {
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus("IN_TRANSIT");
        when(deliveryRepository.findById(1L)).thenReturn(Optional.empty());
        ResponseEntity<Delivery> response = deliveryController.updateDeliveryStatus(1L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(restTemplate, never()).put(anyString(), any());
    }
}