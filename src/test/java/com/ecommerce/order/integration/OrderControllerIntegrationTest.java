package com.ecommerce.order.integration;

import com.ecommerce.order.model.enums.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests over the real Spring context (H2) driving the API through
 * MockMvc. Verifies the full request -> controller -> service -> domain -> DB
 * path and the error translations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String VALID_ORDER = """
            {
              "customerId": 1001,
              "customerEmail": "jane@example.com",
              "customerName": "Jane Doe",
              "shippingAddress": "123 Main St",
              "items": [
                { "productId": 101, "productSku": "SKU-1", "productName": "Widget", "quantity": 2, "unitPrice": 19.99 }
              ]
            }
            """;

    private long createOrderAndReturnId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.orderNumber", is(org.hamcrest.Matchers.startsWith("ORD-"))))
                .andExpect(jsonPath("$.totalAmount", is(39.98)))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    @DisplayName("POST creates an order (201) with computed total and order number")
    void createOrder() throws Exception {
        createOrderAndReturnId();
    }

    @Test
    @DisplayName("POST with no items fails validation (400)")
    void createInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"customerId\": 1001, \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("GET by id and by order number; 404 for unknown")
    void getOrder() throws Exception {
        long id = createOrderAndReturnId();

        MvcResult r = mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andReturn();
        String orderNumber = objectMapper.readTree(r.getResponse().getContentAsString())
                .get("orderNumber").asText();

        mockMvc.perform(get("/api/v1/orders/number/{n}", orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)));

        mockMvc.perform(get("/api/v1/orders/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH advances status; illegal jump returns 409")
    void updateStatus() throws Exception {
        long id = createOrderAndReturnId();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"PROCESSING\", \"reason\": \"verified\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROCESSING")));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"DELIVERED\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancel works while PENDING, then 409 once not PENDING")
    void cancelRules() throws Exception {
        long id = createOrderAndReturnId();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"PROCESSING\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancel a PENDING order succeeds")
    void cancelPending() throws Exception {
        long id = createOrderAndReturnId();
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("list filters by status")
    void listByStatus() throws Exception {
        createOrderAndReturnId();
        mockMvc.perform(get("/api/v1/orders")
                        .param("status", OrderStatus.PENDING.name())
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("statistics endpoint returns totals")
    void statistics() throws Exception {
        createOrderAndReturnId();
        mockMvc.perform(get("/api/v1/orders/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").isNumber())
                .andExpect(jsonPath("$.countByStatus").exists());
    }
}
