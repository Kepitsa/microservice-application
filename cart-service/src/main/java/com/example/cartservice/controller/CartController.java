package com.example.cartservice.controller;

import com.example.cartservice.model.Cart;
import com.example.cartservice.model.CartItem;
import com.example.cartservice.repository.CartItemRepository;
import com.example.cartservice.repository.CartRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final RestTemplate restTemplate;

    public CartController(CartRepository cartRepository, CartItemRepository cartItemRepository, RestTemplate restTemplate) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Cart> getOrCreateCart(@PathVariable Long userId) {
        Optional<Cart> cart = cartRepository.findByUserId(userId);
        if (cart.isPresent()) {
            return ResponseEntity.ok(cart.get());
        } else {
            Cart newCart = new Cart(userId);
            return ResponseEntity.ok(cartRepository.save(newCart));
        }
    }

    @PostMapping("/{userId}/add")
    public ResponseEntity<Cart> addToCart(@PathVariable Long userId, @RequestBody CartItemRequest request) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        Cart cart = cartOpt.orElseGet(() -> cartRepository.save(new Cart(userId)));

        CartItem item = new CartItem(cart.getId(), request.getProductId(), request.getQuantity());
        cartItemRepository.save(item);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/{userId}/remove/{itemId}")
    public ResponseEntity<Cart> removeFromCart(@PathVariable Long userId, @PathVariable Long itemId) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
        if (itemOpt.isEmpty() || !itemOpt.get().getCartId().equals(cartOpt.get().getId())) {
            return ResponseEntity.notFound().build();
        }

        cartItemRepository.delete(itemOpt.get());
        return ResponseEntity.ok(cartOpt.get());
    }

    @GetMapping("/{userId}/total")
    public ResponseEntity<BigDecimal> getTotal(@PathVariable Long userId) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isEmpty()) {
            return ResponseEntity.ok(BigDecimal.ZERO);
        }

        List<CartItem> items = cartItemRepository.findByCartId(cartOpt.get().getId());
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : items) {
            String url = "http://localhost:8080/products/" + item.getProductId();
            try {
                Product product = restTemplate.getForObject(url, Product.class);
                total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            } catch (Exception e) {
                // Логироваение
            }
        }
        return ResponseEntity.ok(total);
    }

    @GetMapping("/{userId}/items")
    public ResponseEntity<List<CartItem>> getCartItems(@PathVariable Long userId) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<CartItem> items = cartItemRepository.findByCartId(cartOpt.get().getId());
        return ResponseEntity.ok(items);
    }
}

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

class Product {
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
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}