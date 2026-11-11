package edu.escuelaing.campusseguro.events.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Checks that the {@code /error} path answers with the standard error JSON. */
class ApiErrorControllerTest {

    private static final String STATUS = "jakarta.servlet.error.status_code";
    private static final String URI = "jakarta.servlet.error.request_uri";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ApiErrorController()).build();
    }

    @Test
    void usesTheStatusAndTheCorrelationIdOfTheFailedRequest() throws Exception {
        mockMvc.perform(get("/error").requestAttr(STATUS, 404).requestAttr(URI, "/api/v1/missing")
                        .header("X-Correlation-ID", "11111111-1111-4111-8111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value("11111111-1111-4111-8111-111111111111"));
    }

    @Test
    void answersAnInternalErrorWhenThereIsNoStatus() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));
    }

    @Test
    void mapsEveryKnownStatusToItsCode() {
        assertEquals(ErrorCode.VALIDATION_ERROR, ErrorCode.fromStatus(400));
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.fromStatus(401));
        assertEquals(ErrorCode.FORBIDDEN, ErrorCode.fromStatus(403));
        assertEquals(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.fromStatus(405));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.fromStatus(415));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromStatus(502));
    }
}
