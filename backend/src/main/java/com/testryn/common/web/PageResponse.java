package com.testryn.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform pagination envelope for list endpoints (Abschnitt 9/10 -- predictable
 * response shape for AI/CI consumers). 0-based page index, matching Spring Data's
 * own convention. Any future paginated endpoint in Testryn should return this same
 * shape rather than inventing a new one per resource.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <S, T> PageResponse<T> from(Page<S> page, List<T> mappedContent) {
        return new PageResponse<>(mappedContent, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
