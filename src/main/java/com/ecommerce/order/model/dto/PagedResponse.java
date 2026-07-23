package com.ecommerce.order.model.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A trimmed, stable pagination envelope so internal paging details don't leak
 * into the public contract.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
