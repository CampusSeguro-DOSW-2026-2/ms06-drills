package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.client.brigade.BrigadierInfo;
import edu.escuelaing.campusseguro.events.client.brigade.StubBrigadeGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.entity.EventBrigadierEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.BrigadierStatus;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.repository.EventBrigadierRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.repository.PostgresIntegrationTest;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Copies brigadiers against a real database with the stub gateways of the local profile. */
class BrigadierSyncIT extends PostgresIntegrationTest {

    @Autowired
    private EventCreationService creation;
    @Autowired
    private BrigadierSyncService sync;
    @Autowired
    private EventRepository events;
    @Autowired
    private EventTypeRepository types;
    @Autowired
    private EventBrigadierRepository brigadiers;
    @Autowired
    private StubBrigadeGateway brigadeStub;

    private final AccessActor coordinator = new AccessActor("coord-it", Set.of(Role.COORDINADOR));

    private UUID scheduledEvent(String name) {
        CreateEventRequest request = new CreateEventRequest(
                name, null, Set.of(types.findByCode("SISMO").orElseThrow().getId()),
                Instant.now().plus(2, ChronoUnit.DAYS),
                List.of(new ScopeItemRequest(ScopeType.ZONE, "z1")), Set.of(Role.ESTUDIANTE));
        return creation.create(request, coordinator).id();
    }

    @Test
    void scheduledEventsGetTheirBrigadiersAndChangesAreCopied() {
        UUID eventId = scheduledEvent("Sync IT");
        brigadeStub.givenBrigadiers(eventId, List.of(
                new BrigadierInfo("u-1", "Ana", "z1", true),
                new BrigadierInfo("u-2", "Luis", "z1", true)));

        assertTrue(sync.syncScheduledEvents() >= 1);

        List<EventBrigadierEntity> first = brigadiers.findByEventId(eventId);
        assertEquals(2, first.size());
        assertTrue(first.stream().allMatch(row -> row.getStatus() == BrigadierStatus.ACTIVE));

        // Ana deactivates and Luis disappears from the list of BEI.
        brigadeStub.givenBrigadiers(eventId, List.of(new BrigadierInfo("u-1", "Ana", "z1", false)));
        sync.syncScheduledEvents();

        List<EventBrigadierEntity> second = brigadiers.findByEventId(eventId);
        assertEquals(2, second.size());
        assertTrue(second.stream().allMatch(row -> row.getStatus() == BrigadierStatus.INACTIVE));
    }

    @Test
    void runningEventsAreSyncedAndFinishedEventsAreFrozen() {
        UUID running = scheduledEvent("Running sync IT");
        UUID finished = scheduledEvent("Finished sync IT");
        setStatus(running, EventStatus.IN_PROGRESS);
        setStatus(finished, EventStatus.FINISHED);
        BrigadierInfo ana = new BrigadierInfo("u-1", "Ana", "z1", true);
        brigadeStub.givenBrigadiers(running, List.of(ana));
        brigadeStub.givenBrigadiers(finished, List.of(ana));

        sync.syncRunningEvents();
        sync.syncScheduledEvents();

        assertEquals(1, brigadiers.findByEventId(running).size());
        assertTrue(brigadiers.findByEventId(finished).isEmpty());
    }

    private void setStatus(UUID eventId, EventStatus status) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setStatus(status);
        events.saveAndFlush(event);
    }
}
