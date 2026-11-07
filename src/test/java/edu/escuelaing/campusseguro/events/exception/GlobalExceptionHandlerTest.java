package edu.escuelaing.campusseguro.events.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Class that test the GlobalExceptionHandler file
 * GlobalExceptionHandlerTest
 */

class GlobalExceptionHandlerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(clock);
    private MockMvc mockMvc;

    record Body(@NotBlank String name) {
    }

    @RestController
    @RequestMapping("/t")
    static class ThrowingController {

        @PostMapping("/valid")
        String valid(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/int")
        String number(@RequestParam int n) {
            return "ok";
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/unauth")
        String unauth() {
            throw new BadCredentialsException("bad");
        }

        @GetMapping("/lock")
        String lock() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }

        @GetMapping("/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("duplicate");
        }

        @GetMapping("/violation")
        String violation() {
            throw new ConstraintViolationException(
                    Validation.buildDefaultValidatorFactory().getValidator().validate(new Body("")));
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void invalidBodyAnswers400WithTheFieldDetail() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/t/valid"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void malformedBodyAnswers400() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void wrongParameterTypeAnswers400WithTheParameterName() throws Exception {
        mockMvc.perform(get("/t/int").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("n"));
    }

    @Test
    void constraintViolationAnswers400WithDetails() throws Exception {
        mockMvc.perform(get("/t/violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void accessDeniedAnswers403() throws Exception {
        mockMvc.perform(get("/t/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void authenticationErrorAnswers401() throws Exception {
        mockMvc.perform(get("/t/unauth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void optimisticLockAnswers409() throws Exception {
        mockMvc.perform(get("/t/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void dataIntegrityAnswers409() throws Exception {
        mockMvc.perform(get("/t/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void unexpectedErrorAnswers500WithoutLeakingTheMessage() throws Exception {
        mockMvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    @Test
    void wrongMethodAnswers405() throws Exception {
        mockMvc.perform(post("/t/denied"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeAnswers415() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void genericStatusesMapToTheirErrorCode() {
        Map<Integer, String> expected = Map.of(
                400, "VALIDATION_ERROR",
                401, "UNAUTHORIZED",
                403, "FORBIDDEN",
                404, "RESOURCE_NOT_FOUND",
                405, "METHOD_NOT_ALLOWED",
                409, "CONFLICT",
                415, "UNSUPPORTED_MEDIA_TYPE",
                503, "EXTERNAL_SERVICE_UNAVAILABLE",
                500, "INTERNAL_ERROR",
                418, "VALIDATION_ERROR");

        expected.forEach((status, code) -> {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new Exception("x"),
                    null,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(status),
                    new ServletWebRequest(new MockHttpServletRequest("GET", "/x")));

            assertEquals(status, response.getStatusCode().value());
            assertEquals(code, ((ErrorResponse) response.getBody()).code(), "status " + status);
        });
    }

    @Test
    void optimisticLockingFailureBaseTypeIsHandled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/eventos/1");

        ResponseEntity<Object> response = handler.handleOptimisticLocking(
                new OptimisticLockingFailureException("conflict"), request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONCURRENT_MODIFICATION", ((ErrorResponse) response.getBody()).code());
    }
}
