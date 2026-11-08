package edu.escuelaing.campusseguro.events.exception;

/** A request field breaks a business rule (for example a start date in the past): answers 400. */
public class InvalidFieldException extends BusinessException {

    private final String field;

    /**
     * @param field   request field with the problem
     * @param message what is wrong with it
     */
    public InvalidFieldException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
