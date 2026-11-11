package edu.escuelaing.campusseguro.events.dto.response;

/**
 * Something that did not go as requested but did not stop the operation, for example a part
 * of the event that stayed pending because another service was down.
 *
 * @param code    short code for the client (SCOPE_PENDING, PARTICIPANTS_PENDING, SAVED_AS_DRAFT, BRIGADIERS_PENDING)
 * @param message explanation
 */
public record EventWarning(String code, String message) {
}
