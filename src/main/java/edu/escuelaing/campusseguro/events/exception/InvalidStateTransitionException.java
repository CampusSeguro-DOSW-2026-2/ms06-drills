package edu.escuelaing.campusseguro.events.exception;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;

/** The event cannot move from its current status to the requested one: answers 409. */
public class InvalidStateTransitionException extends BusinessException {

    /**
     * @param from current status
     * @param to   requested status
     */
    public InvalidStateTransitionException(EventStatus from, EventStatus to) {
        super(ErrorCode.INVALID_STATE_TRANSITION,
                "An event cannot change from " + from + " to " + to);
    }
}
