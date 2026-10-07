package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable JSON shape for paged lists (instead of serialising Spring's Page). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> map) {
        return new PagedResponse<>(page.getContent().stream().map(map).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
