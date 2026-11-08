package edu.escuelaing.campusseguro.events.exception;

/** Another microservice did not answer or answered with an error: answers 503. */
public class ExternalServiceUnavailableException extends BusinessException {

    private final String service;

    /**
     * @param service name of the service that failed (e.g. MS04)
     * @param cause   original error
     */
    public ExternalServiceUnavailableException(String service, Throwable cause) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "Service " + service + " is unavailable. Please try again later.");
        this.service = service;
        initCause(cause);
    }

    public String service() {
        return service;
    }
}
