package edu.escuelaing.campusseguro.events.controller;

import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventDetailResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventFinishResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventPanelResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventSummaryResponse;
import edu.escuelaing.campusseguro.events.dto.response.PageResponse;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.service.EventCreationService;
import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import edu.escuelaing.campusseguro.events.service.EventPanelService;
import edu.escuelaing.campusseguro.events.service.EventQueryService;
import edu.escuelaing.campusseguro.events.service.EventUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Event management. More endpoints are added in the next steps. */
@RestController
@RequestMapping("/api/v1/eventos")
@Tag(name = "Events")
public class EventController {

    private final EventCreationService creation;
    private final EventUpdateService update;
    private final EventPanelService panel;
    private final EventLifecycleService lifecycle;
    private final EventQueryService queries;

    public EventController(
            EventCreationService creation,
            EventUpdateService update,
            EventPanelService panel,
            EventLifecycleService lifecycle,
            EventQueryService queries) {
        this.creation = creation;
        this.update = update;
        this.panel = panel;
        this.lifecycle = lifecycle;
        this.queries = queries;
    }

    /**
     * Creates an event. The coordinator creates real events and the organizer creates drills.
     * The answer says if it was saved as Draft or Scheduled and lists any warning.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COORDINADOR','ORGANIZADOR')")
    @Operation(summary = "Create an event or drill")
    public EventResponse create(@Valid @RequestBody CreateEventRequest request, Authentication authentication) {
        return creation.create(request, AccessActor.from(authentication));
    }

    /**
     * Edits an event. The body replaces every editable field (same body as the creation).
     * Only the coordinator (real events) and the organizer (drills) can do it, and only while
     * the event is Draft, Scheduled or In progress.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COORDINADOR','ORGANIZADOR')")
    @Operation(summary = "Edit an event or drill")
    public EventResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEventRequest request,
            Authentication authentication) {
        return update.update(id, request, AccessActor.from(authentication));
    }

    /**
     * Panel of an event that is In progress: name, route maps and the state of the check-in and
     * incident buttons. Any signed-in user can open it; the start problems are only listed for
     * the coordinator or organizer who manages the event.
     */
    @GetMapping("/{id}/panel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Panel of an event in progress")
    public EventPanelResponse panel(@PathVariable UUID id, Authentication authentication) {
        return panel.panel(id, AccessActor.from(authentication));
    }

    /**
     * Finishes an event that is In progress. Only the coordinator (real events) and the organizer
     * (drills) can do it. The answer has the general metrics of the event and any warning.
     */
    @PatchMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyRole('COORDINADOR','ORGANIZADOR')")
    @Operation(summary = "Finish an event in progress")
    public EventFinishResponse finish(@PathVariable UUID id, Authentication authentication) {
        return lifecycle.finish(id, AccessActor.from(authentication));
    }

    /**
     * Lists events, most recent first, one page at a time. Both filters are optional: {@code title}
     * finds events whose name contains the text, {@code status} keeps one status. An empty page is a
     * normal answer when nothing matches.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINADOR','ORGANIZADOR','ADMIN','AUDITOR')")
    @Operation(summary = "List and search events")
    public PageResponse<EventSummaryResponse> search(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.search(title, status, page, size);
    }

    /**
     * Events In progress that concern the signed-in user (the ones they manage and the ones that
     * invite their role). It feeds the main screen panel.
     */
    @GetMapping("/activos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My events in progress")
    public List<EventSummaryResponse> active(Authentication authentication) {
        return queries.active(AccessActor.from(authentication));
    }

    /**
     * Detail of an event. In progress: real-time indicators. Finished: the saved evaluation.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COORDINADOR','ORGANIZADOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detail and monitoring of an event")
    public EventDetailResponse detail(@PathVariable UUID id) {
        return queries.detail(id);
    }
}
