package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tests how the Gateway headers become the authentication (roles and permissions). */
class IdentityHeadersAuthenticationFilterTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    private final RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final IdentityHeadersAuthenticationFilter filter = new IdentityHeadersAuthenticationFilter(entryPoint);

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String userId, String roles, String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(IdentityHeaders.USER_ID, userId);
        }
        if (roles != null) {
            request.addHeader(IdentityHeaders.USER_ROLE, roles);
        }
        if (permissions != null) {
            request.addHeader(IdentityHeaders.USER_PERMISSION, permissions);
        }
        return request;
    }

    private Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
    }

    @Test
    void permissionsAndKnownRolesBecomeAuthorities() throws Exception {
        MockHttpServletRequest request = request(USER, "AUDITOR,ROL_NUEVO", "ms06:drill:read, ms06:real_event:read");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // ROL_NUEVO was created by MS01 and travels so the event that invites it reaches this user
        assertEquals(Set.of("ROLE_AUDITOR", "ROLE_ROL_NUEVO", "ms06:drill:read", "ms06:real_event:read"), authorities());
        assertEquals(USER, SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void aRoleThatMs06DoesNotKnowStillTravelsForTheParticipants() throws Exception {
        filter.doFilter(request(USER, "MONITOR_EVACUACION", "ms06:drill:read"), new MockHttpServletResponse(), chain);

        assertEquals(Set.of("ROLE_MONITOR_EVACUACION", "ms06:drill:read"), authorities());
    }

    @Test
    void theRolesHeaderIsOptional() throws Exception {
        filter.doFilter(request(USER, null, "ms06:drill:read"), new MockHttpServletResponse(), chain);

        assertEquals(Set.of("ms06:drill:read"), authorities());
    }

    @Test
    void withoutPermissionsTheRequestIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(USER, "COORDINADOR", null), response, chain);

        verify(entryPoint).commence(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void onlyCommasInThePermissionsIsRejected() throws Exception {
        filter.doFilter(request(USER, "COORDINADOR", " , "), new MockHttpServletResponse(), chain);

        verify(entryPoint).commence(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aUserIdThatIsNotAUuidIsRejected() throws Exception {
        filter.doFilter(request("not-a-uuid", "COORDINADOR", "ms06:drill:read"), new MockHttpServletResponse(), chain);

        verify(entryPoint).commence(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void withoutAnyIdentityHeaderTheRequestContinuesAnonymous() throws Exception {
        filter.doFilter(request(null, null, null), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}