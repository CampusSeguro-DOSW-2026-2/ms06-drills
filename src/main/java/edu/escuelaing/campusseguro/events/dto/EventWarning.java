package edu.escuelaing.campusseguro.events.dto;

/**
 * Something that did not go as requested but did not stop the operation
 *
 * @param code    short code for the client (SCOPE_PENDING, PARTICIPANTS_PENDING, SAVED_AS_DRAFT)
 * @param message explanation
 */
public record EventWarning(String code, String message) {
}
