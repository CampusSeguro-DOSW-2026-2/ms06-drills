package edu.escuelaing.campusseguro.events.exception;

import java.util.List;

/** The request uses ids that do not exist or are not active in another service: answers 400. */
public class InvalidReferenceException extends BusinessException {

    private final String field;
    private final List<String> invalidIds;

    /**
     * @param field      request field that has the wrong ids
     * @param invalidIds the ids that were rejected
     */
    public InvalidReferenceException(String field, List<String> invalidIds) {
        super(ErrorCode.VALIDATION_ERROR,
                "Unknown or inactive values in '" + field + "': " + invalidIds);
        this.field = field;
        this.invalidIds = List.copyOf(invalidIds);
    }

    public String field() {
        return field;
    }

    public List<String> invalidIds() {
        return invalidIds;
    }
}
