package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** What can be done with an event depending on its status. */
@Component
public class EventStateRules {

    /** A finished event is read-only; any other status can be edited. */
    public boolean isEditable(EventStatus status) {
        return !isFinal(status);
    }

    /** The start date can only change before the event starts. */
    public boolean isStartDateEditable(EventStatus status) {
        Objects.requireNonNull(status, "status");
        return status == EventStatus.DRAFT || status == EventStatus.SCHEDULED;
    }

    /** FINISHED has no way out. */
    public boolean isFinal(EventStatus status) {
        return Objects.requireNonNull(status, "status") == EventStatus.FINISHED;
    }
}
