package edu.escuelaing.campusseguro.events.security;

/** Technical headers exchanged with the API Gateway (integration standards, section 4). */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String SERVICE_NAME = "X-Service-Name";
    public static final String SERVICE_KEY = "X-Service-Key";

    private IdentityHeaders() {
    }
}
