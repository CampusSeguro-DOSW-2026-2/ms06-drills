package edu.escuelaing.campusseguro.events.controller;

import edu.escuelaing.campusseguro.events.dto.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.EventResponse;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.service.EventCreationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Event management. More endpoints are added in the next steps. */
@RestController
@RequestMapping("/api/v1/eventos")
@Tag(name = "Events")
public class EventController {

    private final EventCreationService creation;

    public EventController(EventCreationService creation) {
        this.creation = creation;
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
}
