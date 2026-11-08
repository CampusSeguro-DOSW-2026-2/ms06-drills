package edu.escuelaing.campusseguro.events.exception;

/**
 * Base of the exceptions thrown when a business rule is broken.
 * It carries the {@link ErrorCode} that decides the HTTP status and the error code
 * of the response; the message is shown to the client.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    /**
     * @param code    error code (also sets the HTTP status)
     * @param message explanation for the client
     */
    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
