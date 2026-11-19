package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Alert type codes of MS06 that are sent to MS03.
 *
 * <p>The codes are the ones of the MS06 event type catalog (for example
 * {@code SISMO}, {@code INCENDIO} or {@code FUGA_QUIMICA}); there is no text
 * translation and no hardcoded mapping. Only the active and alert-eligible
 * types are sent, {@code SIMULACRO} is excluded, and when none remains the
 * generic code {@code OTRO} is sent. {@code OPERATIVA} does not exist in MS06:
 * it is a reserved type of MS03 and is never sent from here.
 */
public final class AlertTypeCodes {

    /** Code sent when the event has no active, alert-eligible type. */
    public static final String OTHER_CODE = "OTRO";

    private AlertTypeCodes() {
    }

    /**
     * Returns the MS06 codes that MS03 receives as the alert types of an event.
     *
     * @param eventTypes types of the event
     * @return sorted and distinct codes, or {@code OTRO} when there are none
     */
    public static List<String> from(Set<EventTypeEntity> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of(OTHER_CODE);
        }
        List<String> codes = eventTypes.stream()
                .filter(Objects::nonNull)
                .filter(EventTypeEntity::isActive)
                .filter(EventTypeEntity::isAlertEligible)
                .filter(type -> !EventTypeEntity.SIMULACRO_CODE.equals(type.getCode()))
                .map(EventTypeEntity::getCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return codes.isEmpty() ? List.of(OTHER_CODE) : codes;
    }
}
