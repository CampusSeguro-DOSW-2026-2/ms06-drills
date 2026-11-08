package edu.escuelaing.campusseguro.events.client.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * An audit record as MS08 receives it (RF-BEI-06). The contract is a proposal until MS08 confirms it.
 *
 * @param idRegistro        record id; MS08 can use it to ignore a record that arrives twice
 * @param tipoRegistro      CREATION, MODIFICATION or FINALIZATION
 * @param usuarioOrigen     user or service that did the action
 * @param accion            action (for example EVENT_CREATED)
 * @param fechaHoraRegistro when the action happened
 * @param evento            event where the action happened; {@code null} if there is none
 * @param recurso           affected resource (for example EVENT:{id})
 * @param resultado         result of the operation
 * @param detalle           short explanation of the change; may be {@code null}
 * @param origen            module that sends the record (always MS06)
 */
public record AuditRecord(
        UUID idRegistro,
        String tipoRegistro,
        String usuarioOrigen,
        String accion,
        Instant fechaHoraRegistro,
        EventRef evento,
        String recurso,
        String resultado,
        String detalle,
        String origen) {

    /** Event of an audit record. */
    public record EventRef(UUID id, String nombre) {
    }
}
