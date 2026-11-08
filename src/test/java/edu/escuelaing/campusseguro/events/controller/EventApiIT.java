package edu.escuelaing.campusseguro.events.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.repository.PostgresIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Calls the endpoints through the real security chain to check roles and error format. */
class EventApiIT extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private EventTypeRepository types;
    @Autowired
    private EventRepository events;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject("user-" + role)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UUID typeId(String code) {
        return types.findByCode(code).orElseThrow().getId();
    }

    private String body(String name, UUID typeId) {
        return "{\"name\":\"" + name + "\",\"typeIds\":[\"" + typeId + "\"]}";
    }

    @Test
    void withoutTokenTheAnswerIs401() throws Exception {
        mvc.perform(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(body("x", typeId("SISMO"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStudentCannotCreateEvents() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(as("ESTUDIANTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", typeId("SISMO"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void theCoordinatorCreatesARealDraft() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("API draft", typeId("SISMO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.simulacro").value(false))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void theCoordinatorCannotCreateADrill() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", typeId("SIMULACRO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theOrganizerCreatesADrill() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(as("ORGANIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("API drill", typeId("SIMULACRO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulacro").value(true));
    }

    @Test
    void aBlankNameIsRejectedWithDetails() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("", typeId("SISMO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void anUnknownTypeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("typeIds"));
    }

    @Test
    void aPastStartDateIsRejected() throws Exception {
        String json = "{\"name\":\"x\",\"typeIds\":[\"" + typeId("SISMO") + "\"],\"startDate\":\"2020-01-01T10:00:00Z\"}";

        mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("startDate"));
    }

    @Test
    void theCatalogListsTheSevenPredefinedTypes() throws Exception {
        mvc.perform(get("/api/v1/tipos-evento").with(as("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='SIMULACRO')]").exists())
                .andExpect(jsonPath("$[?(@.code=='FUGA_QUIMICA')]").exists());
    }

    @Test
    void aStudentCannotReadTheCatalog() throws Exception {
        mvc.perform(get("/api/v1/tipos-evento").with(as("ESTUDIANTE"))).andExpect(status().isForbidden());
    }

    @Test
    void onlyTheCoordinatorAddsTypes() throws Exception {
        String name = "Tipo " + UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"name\":\"" + name + "\"}";

        mvc.perform(post("/api/v1/tipos-evento").with(as("ORGANIZADOR")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tipos-evento").with(as("COORDINADOR")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/v1/tipos-evento").with(as("COORDINADOR")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    /** Creates a draft with the coordinator token and returns its id. */
    private String createDraft(String name) throws Exception {
        String content = mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, typeId("SISMO"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(content, "$.id");
    }

    @Test
    void theCoordinatorEditsADraft() throws Exception {
        String id = createDraft("To edit");

        mvc.perform(put("/api/v1/eventos/" + id).with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Edited", typeId("SISMO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edited"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void aStudentCannotEditEvents() throws Exception {
        mvc.perform(put("/api/v1/eventos/" + UUID.randomUUID()).with(as("ESTUDIANTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", typeId("SISMO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theOrganizerCannotEditARealEvent() throws Exception {
        String id = createDraft("Real event");

        mvc.perform(put("/api/v1/eventos/" + id).with(as("ORGANIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", typeId("SISMO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void editingAnUnknownEventAnswers404() throws Exception {
        mvc.perform(put("/api/v1/eventos/" + UUID.randomUUID()).with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", typeId("SISMO"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void editingWithABlankNameIsRejected() throws Exception {
        String id = createDraft("Blank name");

        mvc.perform(put("/api/v1/eventos/" + id).with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("", typeId("SISMO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    /** Creates a draft with one zone and forces it to In progress, as the start job would do. */
    private String createRunningEvent(String name) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"typeIds\":[\"" + typeId("SISMO") + "\"],"
                + "\"scope\":[{\"type\":\"ZONE\",\"id\":\"z1\"}]}";
        String content = mvc.perform(post("/api/v1/eventos").with(as("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(content, "$.id");
        EventEntity event = events.findById(UUID.fromString(id)).orElseThrow();
        event.setStatus(EventStatus.IN_PROGRESS);
        event.setStartedAt(Instant.now());
        events.saveAndFlush(event);
        return id;
    }

    @Test
    void theListFindsEventsByTitleAndStatus() throws Exception {
        createDraft("Searchable Quake Event");
        createRunningEvent("Searchable Fire Event");

        mvc.perform(get("/api/v1/eventos").param("title", "searchable quake").with(as("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Searchable Quake Event"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));

        mvc.perform(get("/api/v1/eventos").param("title", "searchable").param("status", "IN_PROGRESS")
                        .with(as("COORDINADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Searchable Fire Event"));
    }

    @Test
    void aSearchWithoutResultsIsAnEmptyPage() throws Exception {
        mvc.perform(get("/api/v1/eventos").param("title", "no-such-event-xyz").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theListRejectsAWrongPageSizeAndAnUnknownStatus() throws Exception {
        mvc.perform(get("/api/v1/eventos").param("size", "500").with(as("COORDINADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/eventos").param("status", "NOPE").with(as("COORDINADOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theListAndTheDetailAreClosedToTheCommunity() throws Exception {
        mvc.perform(get("/api/v1/eventos").with(as("ESTUDIANTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos/" + UUID.randomUUID()).with(as("BRIGADISTA")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDetailOfARunningEventHasTheMonitoring() throws Exception {
        String id = createRunningEvent("Detail event");

        mvc.perform(get("/api/v1/eventos/" + id).with(as("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.name").value("Detail event"))
                .andExpect(jsonPath("$.monitoring.checkIn.totalCheckins").value(0))
                .andExpect(jsonPath("$.monitoring.incidents.totalIncidents").value(0))
                .andExpect(jsonPath("$.monitoring.messages[0].code").value("NO_CHECKINS"))
                .andExpect(jsonPath("$.evaluation").doesNotExist());
    }

    @Test
    void theDetailOfAnUnknownEventIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/eventos/" + UUID.randomUUID()).with(as("COORDINADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theActiveEventsAreOpenToAnySignedInUser() throws Exception {
        String id = createRunningEvent("Active for coordinator");

        mvc.perform(get("/api/v1/eventos/activos").with(as("COORDINADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].name").value("Active for coordinator"));
        mvc.perform(get("/api/v1/eventos/activos").with(as("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
        mvc.perform(get("/api/v1/eventos/activos")).andExpect(status().isUnauthorized());
    }

    @Test
    void thePanelOfARunningEventIsOpenToAnySignedInUser() throws Exception {
        String id = createRunningEvent("Panel event");

        mvc.perform(get("/api/v1/eventos/" + id + "/panel").with(as("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Panel event"))
                .andExpect(jsonPath("$.routesAvailable").value(true))
                .andExpect(jsonPath("$.routes[0].id").value("z1"))
                .andExpect(jsonPath("$.checkInEnabled").value(true))
                .andExpect(jsonPath("$.incidentRegistrationEnabled").value(true));
    }

    @Test
    void thePanelNeedsAToken() throws Exception {
        mvc.perform(get("/api/v1/eventos/" + UUID.randomUUID() + "/panel")).andExpect(status().isUnauthorized());
    }

    @Test
    void aDraftHasNoPanel() throws Exception {
        String id = createDraft("No panel yet");

        mvc.perform(get("/api/v1/eventos/" + id + "/panel").with(as("COORDINADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_ACTIVE"));
    }

    @Test
    void thePanelOfAnUnknownEventIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/eventos/" + UUID.randomUUID() + "/panel").with(as("COORDINADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCoordinatorFinishesARunningEventAndGetsTheMetrics() throws Exception {
        String id = createRunningEvent("Finish API");

        mvc.perform(patch("/api/v1/eventos/" + id + "/finalizar").with(as("COORDINADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.endedAt").exists())
                .andExpect(jsonPath("$.evaluation.complete").value(true))
                .andExpect(jsonPath("$.evaluation.noRecords").value(true))
                .andExpect(jsonPath("$.warnings[?(@.code=='NO_RECORDS')]").exists());
    }

    @Test
    void anEventThatIsNotRunningCannotBeFinished() throws Exception {
        String id = createDraft("Finish draft API");

        mvc.perform(patch("/api/v1/eventos/" + id + "/finalizar").with(as("COORDINADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void theOrganizerCannotFinishARealEvent() throws Exception {
        String id = createRunningEvent("Finish forbidden API");

        mvc.perform(patch("/api/v1/eventos/" + id + "/finalizar").with(as("ORGANIZADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStudentCannotFinishEvents() throws Exception {
        mvc.perform(patch("/api/v1/eventos/" + UUID.randomUUID() + "/finalizar").with(as("ESTUDIANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void finishingNeedsATokenAndAKnownEvent() throws Exception {
        mvc.perform(patch("/api/v1/eventos/" + UUID.randomUUID() + "/finalizar"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/eventos/" + UUID.randomUUID() + "/finalizar").with(as("COORDINADOR")))
                .andExpect(status().isNotFound());
    }
}
