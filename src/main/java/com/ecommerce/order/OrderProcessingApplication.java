package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the E-commerce Order Processing System.
 *
 * <p>{@code @EnableScheduling} powers the background job that promotes
 * PENDING orders to PROCESSING every 5 minutes.
 * <p>{@code @EnableCaching} enables the Caffeine (dev) / Redis (prod)
 * cache abstraction used for fast order look-ups by id.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class OrderProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingApplication.class, args);
    }
}
