package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.ErrorResponseWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

/** Tests that security 401 and 403 are written in the standard error format. */
class SecurityErrorWritersTest {

    private final ErrorResponseWriter writer = new ErrorResponseWriter(JsonMapper.builder().build());

    private String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void writerProducesTheJsonErrorBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/eventos");
        request.addHeader("X-Correlation-ID", "11111111-1111-4111-8111-111111111111");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.CONFLICT);

        assertEquals(409, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        String json = body(response);
        assertTrue(json.contains("\"codigo\":\"CONFLICTO\""), json);
        assertTrue(json.contains("\"status\":409"), json);
        assertTrue(json.contains("\"errores\":[]"), json);
        assertTrue(json.contains("\"correlation_id\":\"11111111-1111-4111-8111-111111111111\""), json);
        assertFalse(json.contains("\"path\""), json);
        assertFalse(json.contains("\"timestamp\""), json);
    }

    @Test
    void entryPointAnswers401() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(writer);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest("GET", "/api/v1/eventos"),
                response,
                new BadCredentialsException("bad"));

        assertEquals(401, response.getStatus());
        assertTrue(body(response).contains("\"codigo\":\"NO_AUTENTICADO\""));
    }

    @Test
    void accessDeniedHandlerAnswers403() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(writer);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                new MockHttpServletRequest("GET", "/api/v1/eventos"),
                response,
                new AccessDeniedException("denied"));

        assertEquals(403, response.getStatus());
        assertTrue(body(response).contains("\"codigo\":\"PROHIBIDO\""));
    }
}
