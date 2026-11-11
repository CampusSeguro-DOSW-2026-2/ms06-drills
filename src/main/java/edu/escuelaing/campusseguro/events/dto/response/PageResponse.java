package edu.escuelaing.campusseguro.events.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One page of results. The JSON names follow the API standards (section 9):
 * {@code contenido}, {@code page}, {@code size}, {@code total_elementos}, {@code total_paginas}.
 *
 * @param content       items of this page (empty list when nothing matches)
 * @param page          page number, starting at 0
 * @param size          requested page size
 * @param totalElements total items that match
 * @param totalPages    total pages
 * @param <T>           item type
 */
public record PageResponse<T>(
        @JsonProperty("contenido") List<T> content,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elementos") long totalElements,
        @JsonProperty("total_paginas") int totalPages) {
}
