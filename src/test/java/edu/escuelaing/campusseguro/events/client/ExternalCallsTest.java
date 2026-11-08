package edu.escuelaing.campusseguro.events.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class ExternalCallsTest {

    @Test
    void returnsTheResultOfTheCall() {
        assertThat(ExternalCalls.call("MS99", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void turnsRestClientErrorsIntoUnavailable() {
        ExternalServiceUnavailableException ex = assertThrows(
                ExternalServiceUnavailableException.class,
                () -> ExternalCalls.call("MS99", () -> {
                    throw new RestClientException("boom");
                }));
        assertThat(ex.service()).isEqualTo("MS99");
        assertThat(ex.getCause()).isInstanceOf(RestClientException.class);
    }

    @Test
    void runWorksForCallsWithoutResult() {
        assertThrows(ExternalServiceUnavailableException.class, () -> ExternalCalls.run("MS99", () -> {
            throw new RestClientException("boom");
        }));
    }

    @Test
    void otherExceptionsPassThrough() {
        assertThrows(ResourceNotFoundException.class, () -> ExternalCalls.call("MS99", () -> {
            throw new ResourceNotFoundException("missing");
        }));
    }
}
