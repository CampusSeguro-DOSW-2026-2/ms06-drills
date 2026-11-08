package edu.escuelaing.campusseguro.events.dto.response;

import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Panel of an event that is In progress (RF-EVE-05), shown on the main screen of the users.
 *
 * @param eventId                     event identifier
 * @param name                        event name
 * @param simulacro                   true if the event is a drill
 * @param startedAt                   real start time
 * @param routes                      route maps of the zones and spaces of the event
 * @param routesAvailable             false if CAM did not answer (the routes button stays disabled)
 * @param checkInEnabled              false while CHI has not confirmed the start of the check-in
 * @param incidentRegistrationEnabled false while BEI has not confirmed the start of incidents and brigadiers
 * @param warnings                    problems that need attention; the start problems are only shown
 *                                    to the coordinator or organizer who manages the event
 */
public record EventPanelResponse(
        UUID eventId,
        String name,
        boolean simulacro,
        Instant startedAt,
        List<Route> routes,
        boolean routesAvailable,
        boolean checkInEnabled,
        boolean incidentRegistrationEnabled,
        List<EventWarning> warnings) {

    /** Route map of one building or zone. */
    public record Route(ScopeType type, String id, String name, String url) {
    }
}
