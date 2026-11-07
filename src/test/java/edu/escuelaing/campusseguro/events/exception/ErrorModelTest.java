package edu.escuelaing.campusseguro.events.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorModelTest {

    private final Instant now = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void everyCodeHasAStatusAndAMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertFalse(code.defaultMessage().isBlank(), code.name());
            assertTrue(code.status().isError(), code.name());
        }
    }

    @Test
    void codesMapToTheExpectedStatus() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.status());
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.status());
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.status());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.status());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.CONFLICT.status());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION.status());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.status());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.status());
    }

    @Test
    void ofBuildsTheResponseFromTheCode() {
        ErrorResponse response = ErrorResponse.of(
                now,
                ErrorCode.VALIDATION_ERROR,
                "msg",
                "/api/v1/eventos",
                List.of(new ErrorResponse.Detail("name", "must not be blank")));

        assertEquals(now, response.timestamp());
        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.code());
        assertEquals("msg", response.message());
        assertEquals("/api/v1/eventos", response.path());
        assertEquals(1, response.details().size());
    }

    @Test
    void nullDetailsBecomeAnEmptyList() {
        ErrorResponse response = new ErrorResponse(now, 500, "INTERNAL_ERROR", "m", "/p", null);

        assertTrue(response.details().isEmpty());
    }
}
