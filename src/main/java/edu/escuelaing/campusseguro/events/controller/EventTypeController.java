package edu.escuelaing.campusseguro.events.controller;

import edu.escuelaing.campusseguro.events.dto.CreateEventTypeRequest;
import edu.escuelaing.campusseguro.events.dto.EventTypeResponse;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.service.EventTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Event type catalog. */
@RestController
@RequestMapping("/api/v1/tipos-evento")
@Tag(name = "Event types")
public class EventTypeController {

    private final EventTypeService service;

    public EventTypeController(EventTypeService service) {
        this.service = service;
    }

    /** Active types, to fill the type selector when creating an event. */
    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINADOR','ORGANIZADOR','ADMIN','AUDITOR')")
    @Operation(summary = "List the active event types")
    public List<EventTypeResponse> list() {
        return service.listActive();
    }

    /** Adds a type to the catalog. Only the coordinator can do it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Add an event type (coordinator only)")
    public EventTypeResponse create(@Valid @RequestBody CreateEventTypeRequest request, Authentication authentication) {
        return service.create(request.name(), AccessActor.from(authentication).auditId());
    }
}
