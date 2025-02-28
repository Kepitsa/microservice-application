package com.example.catalogservice.controller;

import com.example.catalogservice.model.Product;
import com.example.catalogservice.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    // Все товары
    @GetMapping
    @Operation(summary = "Получить все товары", description = "Возвращает список всех товаров в каталоге.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список товаров успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Product.class)))
    })
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Товар по ID
    @GetMapping("/{id}")
    @Operation(summary = "Получить товар по ID", description = "Возвращает информацию о товаре по его идентификатору.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар найден",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "404", description = "Товар с указанным ID не найден")
    })
    public ResponseEntity<Product> getProductById(
            @Parameter(description = "Идентификатор товара", required = true) @PathVariable Long id) {
        Optional<Product> product = productRepository.findById(id);
        return product.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Добавить новый товар
    @PostMapping
    @Operation(summary = "Добавить новый товар", description = "Создаёт новый товар в каталоге на основе предоставленных данных.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно добавлен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные товара (например, отрицательная цена или запас)")
    })
    public Product createProduct(
            @Parameter(description = "Данные нового товара", required = true) @RequestBody Product product) {
        return productRepository.save(product);
    }

    // Обновить запас товара
    @PutMapping("/{id}/stock")
    @Operation(summary = "Обновить запас товара", description = "Изменяет количество товара на складе. Уменьшает запас на указанное значение.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запас товара успешно обновлён",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "400", description = "Недостаточно товара на складе для уменьшения"),
            @ApiResponse(responseCode = "404", description = "Товар с указанным ID не найден")
    })
    public ResponseEntity<Product> updateStock(
            @Parameter(description = "Идентификатор товара", required = true) @PathVariable Long id,
            @Parameter(description = "Количество для изменения запаса (положительное — уменьшение)", required = true) @RequestBody StockUpdateRequest request) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Product product = productOpt.get();
        int newStock = product.getStock() - request.getQuantity();
        if (newStock < 0) {
            return ResponseEntity.badRequest().build();
        }
        product.setStock(newStock);
        productRepository.save(product);
        return ResponseEntity.ok(product);
    }
}

class StockUpdateRequest {
    private int quantity;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}