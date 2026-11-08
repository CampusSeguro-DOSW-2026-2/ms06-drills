package edu.escuelaing.campusseguro.events.dto.response;

import java.util.List;

/**
 * One page of results.
 *
 * @param content       items of this page (empty list when nothing matches)
 * @param page          page number, starting at 0
 * @param size          requested page size
 * @param totalElements total items that match
 * @param totalPages    total pages
 * @param <T>           item type
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
