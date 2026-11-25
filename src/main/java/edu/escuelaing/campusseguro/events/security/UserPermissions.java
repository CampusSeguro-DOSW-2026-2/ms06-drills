package edu.escuelaing.campusseguro.events.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Value of {@code X-User-Permission} for calls that MS06 makes on behalf of a user through the API Gateway.
 *
 * <p>The Gateway requires {@code X-User-Id}, {@code X-User-Role} and {@code X-User-Permission} together (and
 * answers 400 otherwise), and MS02, MS03 and MS04 authorize with the permissions only. So:
 * <ul>
 *   <li>when the call acts for the user of the current request, it forwards the permissions that the Gateway
 *       sent in that request;</li>
 *   <li>otherwise (an event that starts on its own, a stored creator, the system user) it sends the configured
 *       permissions of the MS06 system user.</li>
 * </ul>
 */
public final class UserPermissions {

    /**
     * Permissions MS06 needs on other services when it acts as its system user. {@code ms04:history:read} lets MS04
     * answer with the whole event: with {@code ms04:dashboard:read} alone it is treated as a brigadier.
     */
    public static final String DEFAULT_SYSTEM_PERMISSIONS =
            "ms02:campus:read,ms02:route:read,ms03:alert:manage,ms03:alert:manage_drill,ms04:dashboard:read,"
                    + "ms04:history:read";

    /** Same format the Gateway accepts: {@code msNN:resource:action}. */
    private static final Pattern FORMAT = Pattern.compile("^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    private UserPermissions() {
    }

    /**
     * Permissions to send for {@code userId}: the ones of the current request when it belongs to that user,
     * or the system ones.
     */
    public static String forActor(String userId, String systemPermissions) {
        String fromRequest = fromCurrentRequest(userId);
        return fromRequest.isEmpty() ? forSystem(systemPermissions) : fromRequest;
    }

    /** Configured permissions of the system user, or the defaults when none are valid. */
    public static String forSystem(String systemPermissions) {
        String configured = normalize(systemPermissions);
        return configured.isEmpty() ? DEFAULT_SYSTEM_PERMISSIONS : configured;
    }

    /** Valid codes only, without repetitions, comma separated. */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(code -> FORMAT.matcher(code).matches())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static String fromCurrentRequest(String userId) {
        if (userId == null || userId.isBlank()
                || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String requestUser = request.getHeader(IdentityHeaders.USER_ID);
        if (requestUser == null || !requestUser.strip().equalsIgnoreCase(userId.strip())) {
            return "";
        }
        return normalize(request.getHeader(IdentityHeaders.USER_PERMISSION));
    }
}
