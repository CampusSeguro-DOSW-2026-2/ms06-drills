package edu.escuelaing.campusseguro.events.dto.response;

/**
 * Detail of an event.
 *
 * @param event      configuration of the event (title, types, date, status, scope, participants)
 * @param monitoring real-time indicators; only while the event is In progress, otherwise {@code null}
 * @param evaluation saved evaluation; only when the event is Finished, otherwise {@code null}
 */
public record EventDetailResponse(
        EventResponse event, EventMonitoringResponse monitoring, EvaluationResponse evaluation) {
}
