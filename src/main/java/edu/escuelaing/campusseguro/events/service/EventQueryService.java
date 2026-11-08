package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.response.EventDetailResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventSummaryResponse;
import edu.escuelaing.campusseguro.events.dto.response.PageResponse;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import java.util.List;
import java.util.UUID;

/**
 * Queries on events (RF-EVE-03): list and search, detail with monitoring, and the events in progress.
 */
public interface EventQueryService {

    /**
     * Lists events, most recent first.
     *
     * @param title  text the name must contain (case-insensitive); {@code null} or blank for any
     * @param status status to keep; {@code null} for all
     * @param page   page number, starting at 0
     * @param size   page size (1 to 100)
     * @return the page; its content is empty when nothing matches
     */
    PageResponse<EventSummaryResponse> search(String title, EventStatus status, int page, int size);

    /**
     * Detail of an event. While it is In progress the answer has real-time indicators (CHI and BEI);
     * when it is Finished the answer has the saved evaluation. If CHI or BEI do not answer, the
     * detail is still returned and the monitoring says what is unavailable.
     *
     * @throws ResourceNotFoundException if the event does not exist
     */
    EventDetailResponse detail(UUID eventId);

    /**
     * Events In progress that concern the user: the ones the user manages and the ones where the
     * user's role is invited.
     */
    List<EventSummaryResponse> active(AccessActor actor);
}
