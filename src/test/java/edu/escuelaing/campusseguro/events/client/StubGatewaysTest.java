package edu.escuelaing.campusseguro.events.client;

import static org.assertj.core.api.Assertions.assertThat;

import edu.escuelaing.campusseguro.events.client.alert.AlertCommand;
import edu.escuelaing.campusseguro.events.client.alert.StubAlertGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadierInfo;
import edu.escuelaing.campusseguro.events.client.brigade.StubBrigadeGateway;
import edu.escuelaing.campusseguro.events.client.campus.ScopeRoute;
import edu.escuelaing.campusseguro.events.client.campus.StubCampusGateway;
import edu.escuelaing.campusseguro.events.client.checkin.StubCheckInGateway;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StubGatewaysTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-12-01T10:00:00Z"), ZoneOffset.UTC);
    private final UUID eventId = UUID.randomUUID();

    private EventStatePayload payload() {
        return new EventStatePayload(eventId, "Event", false, EventStatus.SCHEDULED,
                Instant.parse("2026-12-02T10:00:00Z"), null, List.of(), Set.of());
    }

    @Test
    void alertStubRemembersWhatItWasAsked() {
        StubAlertGateway stub = new StubAlertGateway();
        AlertCommand command = new AlertCommand(eventId, EventAlertPurpose.FINISH, false, "t", "i",
                List.of(), Set.of(), null, true);

        String id = stub.createAlert(command);
        stub.reschedule(id, Instant.parse("2026-12-03T10:00:00Z"));
        stub.backToDraft(id);
        stub.activate(id);

        assertThat(id).startsWith("stub-alert-");
        assertThat(stub.created()).containsExactly(command);
        assertThat(stub.rescheduled()).containsExactly(id + "@2026-12-03T10:00:00Z");
        assertThat(stub.backToDraft()).containsExactly(id);
        assertThat(stub.activated()).containsExactly(id);
    }

    @Test
    void campusStubAcceptsEverythingAndBuildsFakeRoutes() {
        StubCampusGateway stub = new StubCampusGateway();

        stub.validateScope(ScopeType.ZONE, List.of("z1"));
        List<ScopeRoute> routes = stub.routes(List.of(new ScopeRef(ScopeType.ZONE, "z1")));

        assertThat(routes).containsExactly(new ScopeRoute(ScopeType.ZONE, "z1", "z1", "stub://routes/z1"));
    }

    @Test
    void checkInStubKeepsTheLastStateAndReportsNothing() {
        StubCheckInGateway stub = new StubCheckInGateway(clock);

        stub.pushState(payload());

        assertThat(stub.lastState(eventId)).contains(payload());
        assertThat(stub.indicators(eventId).totalCheckins()).isZero();
        assertThat(stub.indicators(eventId).updatedAt()).isEqualTo(clock.instant());
        assertThat(stub.checkins(eventId, null, 0, 10).items()).isEmpty();
    }

    @Test
    void brigadeStubKeepsTheLastStateAndReportsNothing() {
        StubBrigadeGateway stub = new StubBrigadeGateway(clock);

        stub.pushState(payload());

        assertThat(stub.lastState(eventId)).contains(payload());
        assertThat(stub.brigadiers(eventId).brigadiers()).isEmpty();
        assertThat(stub.indicators(eventId).totalIncidents()).isZero();
    }

    @Test
    void brigadeStubAnswersTheBrigadiersItWasGiven() {
        StubBrigadeGateway stub = new StubBrigadeGateway(clock);
        BrigadierInfo ana = new BrigadierInfo("u-1", "Ana", "z1", true);

        stub.givenBrigadiers(eventId, List.of(ana));

        assertThat(stub.brigadiers(eventId).brigadiers()).containsExactly(ana);
        assertThat(stub.brigadiers(UUID.randomUUID()).brigadiers()).isEmpty();
    }

    @Test
    void statusLabelsMatchTheContract() {
        assertThat(StatusLabels.of(EventStatus.DRAFT)).isEqualTo("BORRADOR");
        assertThat(StatusLabels.of(EventStatus.SCHEDULED)).isEqualTo("PROGRAMADO");
        assertThat(StatusLabels.of(EventStatus.IN_PROGRESS)).isEqualTo("EN_EJECUCION");
        assertThat(StatusLabels.of(EventStatus.FINISHED)).isEqualTo("FINALIZADO");
    }
}
