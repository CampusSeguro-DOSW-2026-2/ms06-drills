package edu.escuelaing.campusseguro.events.exception;

/**
 * MS08 answered 400: the fact does not follow its contract. Sending the same content again would fail
 * again, so the caller must not retry it (unlike {@code ExternalServiceUnavailableException}).
 */
public class AuditRejectedException extends RuntimeException {

    /** @param message short reason; it never contains the body of the fact */
    public AuditRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
