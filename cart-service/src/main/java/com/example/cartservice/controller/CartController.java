package com.example.cartservice.controller;

import com.example.cartservice.model.Cart;
import com.example.cartservice.model.CartItem;
import com.example.cartservice.repository.CartItemRepository;
import com.example.cartservice.repository.CartRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(summary = "Получить или создать корзину", description = "Возвращает существующую корзину пользователя или создаёт новую, если её нет.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Корзина успешно получена или создана",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Cart.class)))
    })
    public ResponseEntity<Cart> getOrCreateCart(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId) {
        Optional<Cart> cart = cartRepository.findByUserId(userId);
        if (cart.isPresent()) {
            return ResponseEntity.ok(cart.get());
        } else {
            Cart newCart = new Cart(userId);
            return ResponseEntity.ok(cartRepository.save(newCart));
        }
    }

    @PostMapping("/{userId}/add")
    @Operation(summary = "Добавить товар в корзину", description = "Добавляет указанный товар в корзину пользователя. Создаёт корзину, если её нет.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно добавлен в корзину",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Cart.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса (например, отрицательное количество или несуществующий товар)")
    })
    public ResponseEntity<Cart> addToCart(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId,
            @Parameter(description = "Данные добавляемого товара (ID товара и количество)", required = true) @RequestBody CartItemRequest request) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        Cart cart = cartOpt.orElseGet(() -> cartRepository.save(new Cart(userId)));

        CartItem item = new CartItem(cart.getId(), request.getProductId(), request.getQuantity());
        cartItemRepository.save(item);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/{userId}/remove/{itemId}")
    @Operation(summary = "Удалить товар из корзины", description = "Удаляет указанный товар из корзины пользователя по его ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно удалён из корзины",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Cart.class))),
            @ApiResponse(responseCode = "404", description = "Корзина или товар с указанным ID не найдены")
    })
    public ResponseEntity<Cart> removeFromCart(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId,
            @Parameter(description = "Идентификатор товара в корзине", required = true) @PathVariable Long itemId) {
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
    @Operation(summary = "Получить общую стоимость корзины", description = "Рассчитывает и возвращает общую стоимость всех товаров в корзине пользователя.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Общая стоимость успешно рассчитана (0, если корзина пуста)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BigDecimal.class)))
    })
    public ResponseEntity<BigDecimal> getTotal(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isEmpty()) {
            return ResponseEntity.ok(BigDecimal.ZERO);
        }

        List<CartItem> items = cartItemRepository.findByCartId(cartOpt.get().getId());
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : items) {
            String url = "http://catalog-service:8080/products/" + item.getProductId();
            try {
                Product product = restTemplate.getForObject(url, Product.class);
                total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            } catch (Exception e) {
                // Логирование
            }
        }
        return ResponseEntity.ok(total);
    }

    @GetMapping("/{userId}/items")
    @Operation(summary = "Получить товары в корзине", description = "Возвращает список товаров, находящихся в корзине пользователя.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список товаров успешно получен (пустой список, если корзина пуста)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CartItem.class)))
    })
    public ResponseEntity<List<CartItem>> getCartItems(
            @Parameter(description = "Идентификатор пользователя", required = true) @PathVariable Long userId) {
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

    public CartItemRequest() {} // Добавлен пустой конструктор для Swagger
    public CartItemRequest(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}

class Product {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stock;

    public Product() {} // Добавлен пустой конструктор для Swagger
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