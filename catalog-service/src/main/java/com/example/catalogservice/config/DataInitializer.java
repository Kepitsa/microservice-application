package com.example.catalogservice.config;

import com.example.catalogservice.model.Product;
import com.example.catalogservice.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer {

    @Autowired
    private ProductRepository productRepository;

    @PostConstruct
    public void init() {
        if (productRepository.count() == 0) {
            // Начальные данные
            productRepository.save(new Product("Биопроводник", "Описание товара 1", new BigDecimal("11200.00"), 10));
            productRepository.save(new Product("Цепь обратной связи", "Описание товара 2", new BigDecimal("11800.00"), 5));
            productRepository.save(new Product("Нанореле", "Описание товара 3", new BigDecimal("12400.00"), 20));
            productRepository.save(new Product("Высокоплотный костный мозг", "Описание товара 3", new BigDecimal("4000.00"), 15));
        }
    }
}