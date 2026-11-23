package edu.escuelaing.campusseguro.events.client.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * An audit fact as MS08 receives it ({@code POST /api/v1/audit/events}, API standards section 12).
 * Field names are the ones of the MS08 contract; empty fields are not sent.
 *
 * @param id              fact id (UUID); MS08 ignores a fact that arrives twice with the same id
 * @param usuarioId       user that did the action (UUID); {@code null} when the system did it
 * @param modulo          module that sends the fact (always MS06)
 * @param accion          action in UPPER_SNAKE_CASE (for example EVENT_STARTED)
 * @param recursoAfectado affected resource as {@code type:id} (for example evento:{id}); may be {@code null}
 * @param resultado       EXITO or FALLO (MS08 only counts EXITO in its indicators)
 * @param fechaHora       when the action happened (ISO 8601)
 * @param correlationId   correlation id of the request that caused the action
 * @param detalle         generic code of the change (at most 100 characters, no personal data); may be {@code null}
 * @param eventoId        event of the fact (UUID); {@code null} if there is none
 * @param tipoEvento      SIMULACRO or REAL; required by MS08 when {@code eventoId} is sent
 * @param atributos       extra flat data (snake_case keys, text, number or boolean values); may be {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditRecord(
        String id,
        @JsonProperty("usuario_id") String usuarioId,
        String modulo,
        String accion,
        @JsonProperty("recurso_afectado") String recursoAfectado,
        String resultado,
        @JsonProperty("fecha_hora") String fechaHora,
        @JsonProperty("correlation_id") String correlationId,
        String detalle,
        @JsonProperty("evento_id") String eventoId,
        @JsonProperty("tipo_evento") String tipoEvento,
        Map<String, Object> atributos) {
}
