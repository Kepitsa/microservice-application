package com.example.cartservice.controller;

import com.example.cartservice.model.Cart;
import com.example.cartservice.repository.CartRepository;
import com.example.cartservice.repository.CartItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class CartControllerTest {

    @InjectMocks
    private CartController cartController;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetOrCreateCart_existingCart() {
        Cart cart = new Cart(1L);
        cart.setId(1L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        ResponseEntity<Cart> response = cartController.getOrCreateCart(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getId());
        assertEquals(1L, response.getBody().getUserId());
    }

    @Test
    public void testGetOrCreateCart_newCart() {
        when(cartRepository.findByUserId(2L)).thenReturn(Optional.empty());
        Cart savedCart = new Cart(2L);
        savedCart.setId(2L);
        when(cartRepository.save(any(Cart.class))).thenReturn(savedCart);

        ResponseEntity<Cart> response = cartController.getOrCreateCart(2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getBody().getId());
        assertEquals(2L, response.getBody().getUserId());
    }
}