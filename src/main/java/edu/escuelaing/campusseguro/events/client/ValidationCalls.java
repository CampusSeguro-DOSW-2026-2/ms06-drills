package edu.escuelaing.campusseguro.events.client;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls a "validate ids" endpoint (same contract MS03 uses with MS01 and MS02): the request
 * is a JSON body and the answer is {@code {"invalidIds": [...]}}. A 400 or 422 with that
 * body means some ids are wrong; any other failure means the service is unavailable.
 */
public final class ValidationCalls {

    private ValidationCalls() {
    }

    /**
     * Posts the payload and returns the ids that the service rejected.
     *
     * @return the invalid ids; empty when everything is valid
     * @throws ExternalServiceUnavailableException if the service fails in any other way
     */
    public static List<String> invalidIds(RestClient client, String service, String path, Object payload) {
        try {
            ValidationResponse response = client.post()
                    .uri(path)
                    .body(payload)
                    .retrieve()
                    .body(ValidationResponse.class);
            return response == null || response.invalidIds() == null ? List.of() : response.invalidIds();
        } catch (RestClientResponseException ex) {
            List<String> rejected = rejectedIds(ex);
            if (!rejected.isEmpty()) {
                return rejected;
            }
            throw new ExternalServiceUnavailableException(service, ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(service, ex);
        }
    }

    private static List<String> rejectedIds(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status != 400 && status != 422) {
            return List.of();
        }
        try {
            ValidationResponse body = ex.getResponseBodyAs(ValidationResponse.class);
            return body == null || body.invalidIds() == null ? List.of() : body.invalidIds();
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    /** Answer of a validation endpoint. */
    record ValidationResponse(List<String> invalidIds) {
    }
}
