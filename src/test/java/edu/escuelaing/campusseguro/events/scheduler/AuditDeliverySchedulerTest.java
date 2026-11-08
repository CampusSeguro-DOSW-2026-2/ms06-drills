package edu.escuelaing.campusseguro.events.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.service.AuditService;
import org.junit.jupiter.api.Test;

/** The scheduler only calls the service and never lets an error escape. */
class AuditDeliverySchedulerTest {

    private final AuditService delivery = mock(AuditService.class);
    private final AuditDeliveryScheduler scheduler = new AuditDeliveryScheduler(delivery);

    @Test
    void callsTheDeliveryService() {
        scheduler.deliver();

        verify(delivery).deliverPending();
    }

    @Test
    void anUnexpectedErrorIsLoggedAndNotThrown() {
        when(delivery.deliverPending()).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(scheduler::deliver);
    }
}
