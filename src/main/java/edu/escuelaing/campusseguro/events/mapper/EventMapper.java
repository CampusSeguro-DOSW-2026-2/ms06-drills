package edu.escuelaing.campusseguro.events.mapper;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventTypeResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Builds {@link EventResponse} objects from entities. */
@Component
public class EventMapper {

    /**
     * Builds the response of an event.
     *
     * @param event    the event
     * @param scope    its scope
     * @param roles    its invited roles
     * @param alerts   its alert links
     * @param warnings warnings to show (empty if none)
     */
    public EventResponse toResponse(
            EventEntity event,
            List<ScopeRef> scope,
            Set<Role> roles,
            List<EventAlertEntity> alerts,
            List<EventWarning> warnings) {

        List<EventTypeResponse> types = event.getTypes().stream()
                .sorted(Comparator.comparing(EventTypeEntity::getName))
                .map(EventTypeResponse::from)
                .toList();
        List<EventResponse.ScopeItem> scopeItems = scope.stream()
                .map(ref -> new EventResponse.ScopeItem(ref.type(), ref.id()))
                .toList();
        List<EventResponse.AlertLink> alertLinks = alerts.stream()
                .map(alert -> new EventResponse.AlertLink(alert.getPurpose(), alert.getAlertId()))
                .toList();
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getStatus(),
                event.isSimulacro(),
                event.getScheduledStart(),
                event.getStartedAt(),
                event.getEndedAt(),
                types,
                scopeItems,
                new TreeSet<>(roles),
                alertLinks,
                event.getCreatedBy(),
                event.getCreatedAt(),
                warnings);
    }
}
