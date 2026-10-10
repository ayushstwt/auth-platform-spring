package com.iosocket.userservice.dto;

import org.springframework.data.domain.Page;

public record PageMetadata(
        int currentPage,
        int pageSize,
        int totalPages,
        long totalItems
) {
    public static PageMetadata from(Page<?> page) {
        return new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}
