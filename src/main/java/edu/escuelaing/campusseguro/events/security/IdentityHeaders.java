package edu.escuelaing.campusseguro.events.security;

/** Technical headers exchanged with the API Gateway (integration standards, section 4). */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    /**
     * Effective permissions of the user, comma separated; the Gateway fills it from the token and requires it
     * together with {@link #USER_ID} and {@link #USER_ROLE}.
     */
    public static final String USER_PERMISSION = "X-User-Permission";
    public static final String SERVICE_NAME = "X-Service-Name";
    public static final String SERVICE_KEY = "X-Service-Key";

    private IdentityHeaders() {
    }
}
