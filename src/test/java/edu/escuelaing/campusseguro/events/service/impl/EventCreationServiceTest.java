package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.campus.CampusGateway;
import edu.escuelaing.campusseguro.events.client.identity.IdentityGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.EventAccessPolicy;
import edu.escuelaing.campusseguro.events.security.Role;
import edu.escuelaing.campusseguro.events.mapper.EventMapper;
import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import edu.escuelaing.campusseguro.events.service.SchedulingOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



/** Tests the creation rules with mocked repositories and gateways. */
class EventCreationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T14:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-12-01T15:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
    private final EventScopeItemRepository scopeItems = mock(EventScopeItemRepository.class);
    private final EventParticipantRepository participants = mock(EventParticipantRepository.class);
    private final EventStateService stateService = mock(EventStateService.class);
    private final EventLifecycleService lifecycle = mock(EventLifecycleService.class);
    private final CampusGateway campus = mock(CampusGateway.class);
    private final IdentityGateway identity = mock(IdentityGateway.class);
    private final EventCreationServiceImpl service = new EventCreationServiceImpl(
            events, eventTypes, scopeItems, participants, stateService, lifecycle, campus, identity,
            new EventAccessPolicy(), new EventMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final AccessActor coordinator = new AccessActor("coord-1", Set.of(Role.COORDINADOR));
    private final AccessActor organizer = new AccessActor("org-1", Set.of(Role.ORGANIZADOR));

    private EventTypeEntity sismo;
    private EventTypeEntity simulacro;

    private EventTypeEntity type(String code, boolean active) {
        EventTypeEntity entity = new EventTypeEntity();
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        entity.setCode(code);
        entity.setName(code);
        entity.setActive(active);
        return entity;
    }

    @BeforeEach
    void setUp() {
        sismo = type("SISMO", true);
        simulacro = type(EventTypeEntity.SIMULACRO_CODE, true);
        when(eventTypes.findByIdIn(any())).thenAnswer(call -> {
            Set<UUID> ids = Set.copyOf(call.<java.util.Collection<UUID>>getArgument(0));
            return List.of(sismo, simulacro).stream().filter(t -> ids.contains(t.getId())).toList();
        });
        when(events.save(any(EventEntity.class))).thenAnswer(call -> {
            EventEntity event = call.getArgument(0);
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
            return event;
        });
        when(lifecycle.schedule(any(), any(), any(), anyString()))
                .thenReturn(new SchedulingOutcome(true, List.of(), List.of()));
    }

    private CreateEventRequest request(Set<UUID> typeIds, Instant start, List<ScopeItemRequest> scope, Set<Role> roles) {
        return new CreateEventRequest("  Evacuation  ", "desc", typeIds, start, scope, roles);
    }

    private List<ScopeItemRequest> zone() {
        return List.of(new ScopeItemRequest(ScopeType.ZONE, "z1"));
    }

    @Test
    void savesADraftWhenThereIsNoStartDate() {
        EventResponse response = service.create(
                request(Set.of(sismo.getId()), null, null, null), coordinator);

        assertEquals(EventStatus.DRAFT, response.status());
        assertFalse(response.simulacro());
        assertEquals("Evacuation", response.name());
        assertEquals("coord-1", response.createdBy());
        assertTrue(response.warnings().isEmpty());
        verify(stateService).recordCreation(any(EventEntity.class), anyString());
        verify(lifecycle, never()).schedule(any(), any(), any(), anyString());
    }

    @Test
    void triesToScheduleWhenDateScopeAndRolesAreComplete() {
        service.create(request(Set.of(sismo.getId()), FUTURE, zone(), Set.of(Role.ESTUDIANTE)), coordinator);

        verify(lifecycle).schedule(any(EventEntity.class), any(), any(), anyString());
        verify(campus).validateScope(ScopeType.ZONE, List.of("z1"));
        verify(identity).validateRoles(Set.of(Role.ESTUDIANTE));
    }

    @Test
    void doesNotScheduleWithoutScope() {
        service.create(request(Set.of(sismo.getId()), FUTURE, null, Set.of(Role.ESTUDIANTE)), coordinator);

        verify(lifecycle, never()).schedule(any(), any(), any(), anyString());
    }

    @Test
    void doesNotScheduleWithoutParticipants() {
        service.create(request(Set.of(sismo.getId()), FUTURE, zone(), null), coordinator);

        verify(lifecycle, never()).schedule(any(), any(), any(), anyString());
    }

    @Test
    void includesTheWarningsOfTheSchedulingAttempt() {
        when(lifecycle.schedule(any(), any(), any(), anyString())).thenReturn(new SchedulingOutcome(
                false, List.of(new edu.escuelaing.campusseguro.events.dto.response.EventWarning("SAVED_AS_DRAFT", "x")), List.of()));

        EventResponse response = service.create(
                request(Set.of(sismo.getId()), FUTURE, zone(), Set.of(Role.ESTUDIANTE)), coordinator);

        assertEquals(EventStatus.DRAFT, response.status());
        assertEquals("SAVED_AS_DRAFT", response.warnings().get(0).code());
    }

    @Test
    void rejectsAStartDateInThePast() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.create(
                request(Set.of(sismo.getId()), NOW.minusSeconds(60), null, null), coordinator));

        assertEquals("startDate", ex.field());
        verify(events, never()).save(any(EventEntity.class));
    }

    @Test
    void rejectsAStartDateEqualToNow() {
        assertThrows(InvalidFieldException.class, () -> service.create(
                request(Set.of(sismo.getId()), NOW, null, null), coordinator));
    }

    @Test
    void theCoordinatorCannotCreateADrill() {
        assertThrows(AccessDeniedException.class, () -> service.create(
                request(Set.of(simulacro.getId()), null, null, null), coordinator));
        verify(events, never()).save(any(EventEntity.class));
    }

    @Test
    void theOrganizerCannotCreateARealEvent() {
        assertThrows(AccessDeniedException.class, () -> service.create(
                request(Set.of(sismo.getId()), null, null, null), organizer));
    }

    @Test
    void anActorWithoutRolesCannotCreate() {
        assertThrows(AccessDeniedException.class, () -> service.create(
                request(Set.of(sismo.getId()), null, null, null), new AccessActor("x", Set.of())));
    }

    @Test
    void theOrganizerCreatesADrillWithSeveralTypes() {
        EventResponse response = service.create(
                request(Set.of(simulacro.getId(), sismo.getId()), null, null, null), organizer);

        assertTrue(response.simulacro());
        assertEquals(2, response.types().size());
    }

    @Test
    void rejectsUnknownTypes() {
        UUID unknown = UUID.randomUUID();

        InvalidReferenceException ex = assertThrows(InvalidReferenceException.class, () -> service.create(
                request(Set.of(sismo.getId(), unknown), null, null, null), coordinator));

        assertEquals("typeIds", ex.field());
        assertEquals(List.of(unknown.toString()), ex.invalidIds());
    }

    @Test
    void rejectsInactiveTypes() {
        sismo.setActive(false);

        assertThrows(InvalidReferenceException.class, () -> service.create(
                request(Set.of(sismo.getId()), null, null, null), coordinator));
    }

    @Test
    void rolesOutsideTheCommunityCannotBeInvited() {
        InvalidReferenceException ex = assertThrows(InvalidReferenceException.class, () -> service.create(
                request(Set.of(sismo.getId()), null, null, Set.of(Role.ESTUDIANTE, Role.ADMIN)), coordinator));

        assertEquals("participantRoles", ex.field());
        assertEquals(List.of("ADMIN"), ex.invalidIds());
    }

    @Test
    void invalidScopeFromCampusIsRejected() {
        doThrow(new InvalidReferenceException("scope", List.of("z9"))).when(campus).validateScope(any(), any());

        assertThrows(InvalidReferenceException.class, () -> service.create(
                request(Set.of(sismo.getId()), null, zone(), null), coordinator));
        verify(events, never()).save(any(EventEntity.class));
    }

    @Test
    void campusDownLeavesTheScopePendingAndTheEventInDraft() {
        doThrow(new ExternalServiceUnavailableException("MS02", new RuntimeException()))
                .when(campus).validateScope(any(), any());

        EventResponse response = service.create(
                request(Set.of(sismo.getId()), FUTURE, zone(), Set.of(Role.ESTUDIANTE)), coordinator);

        assertEquals(EventStatus.DRAFT, response.status());
        assertTrue(response.scope().isEmpty());
        assertEquals("SCOPE_PENDING", response.warnings().get(0).code());
        verify(lifecycle, never()).schedule(any(), any(), any(), anyString());
    }

    @Test
    void identityDownLeavesTheParticipantsPendingAndTheEventInDraft() {
        doThrow(new ExternalServiceUnavailableException("MS01", new RuntimeException()))
                .when(identity).validateRoles(any());

        EventResponse response = service.create(
                request(Set.of(sismo.getId()), FUTURE, zone(), Set.of(Role.ESTUDIANTE)), coordinator);

        assertEquals(EventStatus.DRAFT, response.status());
        assertTrue(response.participantRoles().isEmpty());
        assertEquals("PARTICIPANTS_PENDING", response.warnings().get(0).code());
        verify(lifecycle, never()).schedule(any(), any(), any(), anyString());
    }

    @Test
    void chiDownPropagatesSoNothingIsSaved() {
        when(lifecycle.schedule(any(), any(), any(), anyString()))
                .thenThrow(new ExternalServiceUnavailableException("MS04", new RuntimeException()));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.create(
                request(Set.of(sismo.getId()), FUTURE, zone(), Set.of(Role.ESTUDIANTE)), coordinator));
    }

    @Test
    void duplicatedScopeItemsAreSavedOnce() {
        List<ScopeItemRequest> repeated = List.of(
                new ScopeItemRequest(ScopeType.ZONE, "z1"), new ScopeItemRequest(ScopeType.ZONE, "z1"));

        EventResponse response = service.create(request(Set.of(sismo.getId()), null, repeated, null), coordinator);

        assertEquals(1, response.scope().size());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventScopeItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(scopeItems).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void validatesBuildingsAndZonesSeparately() {
        List<ScopeItemRequest> mixed = List.of(
                new ScopeItemRequest(ScopeType.BUILDING, "b1"), new ScopeItemRequest(ScopeType.ZONE, "z1"));

        service.create(request(Set.of(sismo.getId()), null, mixed, null), coordinator);

        verify(campus).validateScope(ScopeType.BUILDING, List.of("b1"));
        verify(campus).validateScope(ScopeType.ZONE, List.of("z1"));
    }
}
