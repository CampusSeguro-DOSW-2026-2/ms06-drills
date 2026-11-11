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

    private static final String CORRELATION = "11111111-1111-4111-8111-111111111111";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
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
        mockMvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")
                        .header("X-Correlation-ID", CORRELATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.errores[0].campo").value("name"))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty())
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION))
                // The old fields must be gone: the Gateway rejects any other contract.
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void malformedBodyAnswers400() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void wrongParameterTypeAnswers400WithTheParameterName() throws Exception {
        mockMvc.perform(get("/t/int").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("n"));
    }

    @Test
    void constraintViolationAnswers400WithDetails() throws Exception {
        mockMvc.perform(get("/t/violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("name"));
    }

    @Test
    void accessDeniedAnswers403() throws Exception {
        mockMvc.perform(get("/t/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void authenticationErrorAnswers401() throws Exception {
        mockMvc.perform(get("/t/unauth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void optimisticLockAnswers409() throws Exception {
        mockMvc.perform(get("/t/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MODIFICACION_CONCURRENTE"));
    }

    @Test
    void dataIntegrityAnswers409() throws Exception {
        mockMvc.perform(get("/t/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void unexpectedErrorAnswers500WithoutLeakingTheMessage() throws Exception {
        mockMvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    @Test
    void wrongMethodAnswers405() throws Exception {
        mockMvc.perform(post("/t/denied"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void wrongContentTypeAnswers415() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    @Test
    void genericStatusesMapToTheirErrorCode() {
        Map<Integer, String> expected = Map.of(
                400, "VALIDACION",
                401, "NO_AUTENTICADO",
                403, "PROHIBIDO",
                404, "NO_ENCONTRADO",
                405, "METODO_NO_PERMITIDO",
                409, "CONFLICTO",
                415, "TIPO_NO_SOPORTADO",
                503, "SERVICIO_EXTERNO_NO_DISPONIBLE",
                500, "ERROR_INTERNO",
                418, "VALIDACION");

        expected.forEach((status, code) -> {
            ResponseEntity<Object> response = handler.handleExceptionInternal(
                    new Exception("x"),
                    null,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(status),
                    new ServletWebRequest(new MockHttpServletRequest("GET", "/x")));

            assertEquals(status, response.getStatusCode().value());
            assertEquals(code, ((ErrorResponse) response.getBody()).codigo(), "status " + status);
        });
    }

    @Test
    void optimisticLockingFailureBaseTypeIsHandled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/eventos/1");

        ResponseEntity<Object> response = handler.handleOptimisticLocking(
                new OptimisticLockingFailureException("conflict"), request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("MODIFICACION_CONCURRENTE", ((ErrorResponse) response.getBody()).codigo());
    }
}
