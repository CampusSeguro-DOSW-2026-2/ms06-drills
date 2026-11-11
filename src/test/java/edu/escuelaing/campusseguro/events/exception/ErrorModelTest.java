package edu.escuelaing.campusseguro.events.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Tests the error catalog and the error body (API standards, sections 7.1 and 7.2). */
class ErrorModelTest {

    @Test
    void everyCodeHasAStatusACodigoAndAMessage() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertFalse(code.defaultMessage().isBlank(), code.name());
            assertTrue(code.status().isError(), code.name());
            assertTrue(code.codigo().matches("[A-Z_]+"), code.name());
            assertTrue(seen.add(code.codigo()), "repeated codigo " + code.codigo());
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
    void theStandardCodesUseTheCatalogNames() {
        assertEquals("VALIDACION", ErrorCode.VALIDATION_ERROR.codigo());
        assertEquals("NO_AUTENTICADO", ErrorCode.UNAUTHORIZED.codigo());
        assertEquals("PROHIBIDO", ErrorCode.FORBIDDEN.codigo());
        assertEquals("NO_ENCONTRADO", ErrorCode.RESOURCE_NOT_FOUND.codigo());
        assertEquals("CONFLICTO", ErrorCode.CONFLICT.codigo());
    }

    @Test
    void ofBuildsTheResponseFromTheCode() {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "msg",
                List.of(new ErrorResponse.Detail("name", "must not be blank")),
                "corr-1");

        assertEquals(400, response.status());
        assertEquals("VALIDACION", response.codigo());
        assertEquals("msg", response.mensaje());
        assertEquals("corr-1", response.correlationId());
        assertEquals(1, response.errores().size());
        assertEquals("name", response.errores().get(0).campo());
    }

    @Test
    void nullErrorsBecomeAnEmptyList() {
        ErrorResponse response = new ErrorResponse(500, "ERROR_INTERNO", "m", null, "c");

        assertTrue(response.errores().isEmpty());
    }
}
