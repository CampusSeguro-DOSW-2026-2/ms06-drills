package edu.escuelaing.campusseguro.events.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class UserPermissionsTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void currentRequest(String userId, String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, userId);
        request.addHeader(IdentityHeaders.USER_PERMISSION, permissions);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void forwardsThePermissionsOfTheCurrentUser() {
        currentRequest(USER, "ms03:alert:manage, ms06:real_event:manage");

        assertThat(UserPermissions.forActor(USER, "ms03:alert:manage_drill"))
                .isEqualTo("ms03:alert:manage,ms06:real_event:manage");
    }

    @Test
    void usesTheSystemPermissionsForAnotherUser() {
        currentRequest(USER, "ms03:alert:manage");

        assertThat(UserPermissions.forActor("00000000-0000-4000-8000-000000000000", "ms04:dashboard:read"))
                .isEqualTo("ms04:dashboard:read");
    }

    @Test
    void usesTheSystemPermissionsOutsideARequest() {
        assertThat(UserPermissions.forActor(USER, "ms03:alert:manage_drill"))
                .isEqualTo("ms03:alert:manage_drill");
    }

    @Test
    void fallsBackToTheDefaultsWhenTheConfigurationIsEmptyOrInvalid() {
        assertThat(UserPermissions.forSystem("")).isEqualTo(UserPermissions.DEFAULT_SYSTEM_PERMISSIONS);
        assertThat(UserPermissions.forSystem("ADMIN, not a code")).isEqualTo(UserPermissions.DEFAULT_SYSTEM_PERMISSIONS);
    }

    @Test
    void ignoresInvalidAndRepeatedCodes() {
        assertThat(UserPermissions.normalize("ms02:campus:read, ADMIN ,ms02:campus:read,ms04:dashboard:read"))
                .isEqualTo("ms02:campus:read,ms04:dashboard:read");
    }

    @Test
    void anEmptyRequestPermissionUsesTheSystemOnes() {
        currentRequest(USER, " ");

        assertThat(UserPermissions.forActor(USER, "ms03:alert:manage")).isEqualTo("ms03:alert:manage");
    }
}
