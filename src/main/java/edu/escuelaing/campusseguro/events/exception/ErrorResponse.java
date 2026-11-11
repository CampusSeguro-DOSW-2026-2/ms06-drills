package edu.escuelaing.campusseguro.events.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Body of every error answer. It has exactly these five fields (API standards, section 7.1):
 * {@code status}, {@code codigo}, {@code mensaje}, {@code errores} and {@code correlation_id}.
 *
 * @param status        HTTP status
 * @param codigo        stable code from the catalog (for example {@code NO_ENCONTRADO})
 * @param mensaje       explanation for the user
 * @param errores       one item per invalid field; empty when the error has no field
 * @param correlationId value of the {@code X-Correlation-ID} header of the request
 */
public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<Detail> errores,
        @JsonProperty("correlation_id") String correlationId) {

    public ErrorResponse {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    /** One invalid field and why. */
    public record Detail(String campo, String mensaje) {
    }

    /** Builds the response from a catalog code. */
    public static ErrorResponse of(ErrorCode code, String message, List<Detail> errors, String correlationId) {
        return new ErrorResponse(code.status().value(), code.codigo(), message, errors, correlationId);
    }
}
