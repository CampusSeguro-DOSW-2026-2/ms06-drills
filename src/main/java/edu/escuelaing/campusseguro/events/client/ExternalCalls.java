package edu.escuelaing.campusseguro.events.client;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.function.Supplier;
import org.springframework.web.client.RestClientException;

/**
 * Runs a call to another service and turns any HTTP or network failure into an
 * {@link ExternalServiceUnavailableException}. Business exceptions pass through unchanged.
 */
public final class ExternalCalls {

    private ExternalCalls() {
    }

    /**
     * Runs the call and returns its result.
     *
     * @param service name of the called service (used in the error message)
     * @param call    the call to run
     */
    public static <T> T call(String service, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(service, ex);
        }
    }

    /** Same as {@link #call} for calls that return nothing. */
    public static void run(String service, Runnable call) {
        call(service, () -> {
            call.run();
            return null;
        });
    }
}
