package edu.escuelaing.campusseguro.events.exception;

import java.time.Instant;
import java.util.List;

/**
 * Record that expose the required information of an error
 */

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<Detail> details) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record Detail(String field, String message) {
    }

    public static ErrorResponse of(
            Instant timestamp,
            ErrorCode code,
            String message,
            String path,
            List<Detail> details) {

        return new ErrorResponse(
                timestamp,
                code.status().value(),
                code.name(),
                message,
                path,
                details);
    }
}
