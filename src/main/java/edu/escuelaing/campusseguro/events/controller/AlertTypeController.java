package edu.escuelaing.campusseguro.events.controller;

import edu.escuelaing.campusseguro.events.dto.response.AlertTypeResponse;
import edu.escuelaing.campusseguro.events.service.EventTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alert type catalog for MS03.
 *
 * <p>MS06 is the source of truth of the alert types: it serves the active and
 * alert-eligible types of the event type catalog. {@code SIMULACRO} is not
 * included because a drill is not an alert type.
 */
@RestController
@RequestMapping("/api/v1/tipos-alerta")
@Tag(name = "Alert types")
public class AlertTypeController {

    private final EventTypeService service;

    public AlertTypeController(EventTypeService service) {
        this.service = service;
    }

    /**
     * Active and alert-eligible types, sorted by name, to replicate them in MS03.
     *
     * <p>Internal route: the Gateway authenticates it with {@code X-Service-Name: ms03} from the
     * service key; it does not use user permissions.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SERVICE_MS03')")
    @Operation(summary = "List the active and alert-eligible types for MS03",
            description = "Internal catalog for MS03. The Gateway authenticates it with "
                    + "X-Service-Name: ms03; it is not a user endpoint.")
    public List<AlertTypeResponse> list() {
        return service.listAlertTypes();
    }
}
