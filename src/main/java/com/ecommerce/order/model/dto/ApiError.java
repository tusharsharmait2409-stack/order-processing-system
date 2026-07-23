package com.ecommerce.order.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response body returned for every failed request, so clients
 * get one predictable shape regardless of what went wrong.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    /** A single field-level validation failure. */
    public record FieldViolation(String field, String message) {
    }
}
