package edu.escuelaing.campusseguro.events.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.campus.CampusGateway;
import edu.escuelaing.campusseguro.events.client.campus.ScopeRoute;
import edu.escuelaing.campusseguro.events.dto.response.EventPanelResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventStartFailureEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ExternalModule;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventStartFailureRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.EventAccessPolicy;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests the event panel with mocked repositories and CAM. */
class EventPanelServiceTest {

    private static final Instant STARTED = Instant.parse("2026-12-01T15:00:05Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventScopeItemRepository scopeItems = mock(EventScopeItemRepository.class);
    private final EventStartFailureRepository failures = mock(EventStartFailureRepository.class);
    private final CampusGateway campus = mock(CampusGateway.class);
    private final EventPanelServiceImpl service =
            new EventPanelServiceImpl(events, scopeItems, failures, campus, new EventAccessPolicy());

    private final AccessActor student = new AccessActor("s-1", Set.of(Role.ESTUDIANTE));
    private final AccessActor organizer = new AccessActor("o-1", Set.of(Role.ORGANIZADOR));
    private EventEntity event;

    @BeforeEach
    void setUp() {
        event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setName("Evacuation drill");
        event.setSimulacro(true);
        event.setStatus(EventStatus.IN_PROGRESS);
        event.setStartedAt(STARTED);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));

        EventScopeItemEntity item = new EventScopeItemEntity();
        item.setScopeType(ScopeType.ZONE);
        item.setScopeRefId("z1");
        when(scopeItems.findByEventId(event.getId())).thenReturn(List.of(item));
        when(campus.routes(anyCollection())).thenReturn(List.of(
                new ScopeRoute(ScopeType.ZONE, "z1", "Zone 1", "https://maps/z1")));
    }

    private void pending(ExternalModule module) {
        EventStartFailureEntity row = new EventStartFailureEntity();
        row.setEventId(event.getId());
        row.setModule(module);
        when(failures.findByEventIdAndResolvedAtIsNull(event.getId())).thenReturn(List.of(row));
    }

    @Test
    void returnsTheNameTheRoutesAndAllTheButtonsEnabled() {
        EventPanelResponse panel = service.panel(event.getId(), student);

        assertEquals("Evacuation drill", panel.name());
        assertEquals(STARTED, panel.startedAt());
        assertTrue(panel.simulacro());
        assertEquals(List.of(new EventPanelResponse.Route(ScopeType.ZONE, "z1", "Zone 1", "https://maps/z1")),
                panel.routes());
        assertTrue(panel.routesAvailable());
        assertTrue(panel.checkInEnabled());
        assertTrue(panel.incidentRegistrationEnabled());
        assertTrue(panel.warnings().isEmpty());
    }

    @Test
    void withoutCamThePanelIsReturnedWithoutRoutes() {
        when(campus.routes(anyCollection())).thenThrow(
                new ExternalServiceUnavailableException("MS02", new RuntimeException("down")));

        EventPanelResponse panel = service.panel(event.getId(), student);

        assertFalse(panel.routesAvailable());
        assertTrue(panel.routes().isEmpty());
        assertEquals(List.of("ROUTES_UNAVAILABLE"), panel.warnings().stream().map(EventWarning::code).toList());
        assertTrue(panel.checkInEnabled());
    }

    @Test
    void whileChiHasNotConfirmedTheCheckInStaysDisabled() {
        pending(ExternalModule.CHI);

        EventPanelResponse panel = service.panel(event.getId(), student);

        assertFalse(panel.checkInEnabled());
        assertTrue(panel.incidentRegistrationEnabled());
    }

    @Test
    void whileBeiHasNotConfirmedTheIncidentRegistrationStaysDisabled() {
        pending(ExternalModule.BEI);

        EventPanelResponse panel = service.panel(event.getId(), student);

        assertTrue(panel.checkInEnabled());
        assertFalse(panel.incidentRegistrationEnabled());
    }

    @Test
    void whoManagesTheEventSeesTheStartProblems() {
        pending(ExternalModule.ALE);

        EventPanelResponse panel = service.panel(event.getId(), organizer);

        assertEquals(List.of("START_PENDING_ALE"), panel.warnings().stream().map(EventWarning::code).toList());
    }

    @Test
    void otherUsersDoNotSeeTheStartProblems() {
        pending(ExternalModule.ALE);

        assertTrue(service.panel(event.getId(), student).warnings().isEmpty());
    }

    @Test
    void anEventThatIsNotInProgressHasNoPanel() {
        event.setStatus(EventStatus.SCHEDULED);

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.panel(event.getId(), student));

        assertEquals(ErrorCode.EVENT_NOT_ACTIVE, error.code());
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.panel(UUID.randomUUID(), student));
    }
}
