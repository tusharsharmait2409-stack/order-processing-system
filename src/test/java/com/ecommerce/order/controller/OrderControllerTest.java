package com.ecommerce.order.controller;

import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.enums.OrderStatus;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests: exercise the controller + validation + exception handling
 * with a mocked {@link OrderService} (no DB, no full context).
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private OrderResponse sample() {
        return new OrderResponse(1L, "ORD-20260723-ABC12345", 1001L, "a@b.com", "Jane",
                "1 St", "note", OrderStatus.PENDING, new BigDecimal("39.98"),
                List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("POST creates an order -> 201")
    void create() throws Exception {
        when(orderService.createOrder(any())).thenReturn(sample());
        String body = """
                { "customerId": 1001, "items": [
                  { "productId": 101, "productSku": "SKU-1", "productName": "Widget", "quantity": 2, "unitPrice": 19.99 } ] }
                """;
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber", is("ORD-20260723-ABC12345")));
    }

    @Test
    @DisplayName("POST with no items -> 400 with fieldErrors")
    void createInvalid() throws Exception {
        String body = "{ \"customerId\": 1001, \"items\": [] }";
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("GET unknown id -> 404")
    void getNotFound() throws Exception {
        when(orderService.getOrder(99L)).thenThrow(new OrderNotFoundException(99L));
        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
