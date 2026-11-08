package edu.escuelaing.campusseguro.events.exception;

/** The requested resource does not exist: answers 404. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
