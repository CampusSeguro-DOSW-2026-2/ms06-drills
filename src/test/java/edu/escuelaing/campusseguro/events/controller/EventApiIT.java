package edu.escuelaing.campusseguro.events.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.repository.PostgresIntegrationTest;
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
}
