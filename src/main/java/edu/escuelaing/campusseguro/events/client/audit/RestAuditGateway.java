package edu.escuelaing.campusseguro.events.client.audit;

import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import edu.escuelaing.campusseguro.events.exception.AuditRejectedException;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.IdentityHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real {@link AuditGateway}. It posts each fact to MS08 ({@code POST /api/v1/audit/events}) through the
 * API Gateway with the MS06 service key ({@code X-Service-Key}); MS08 accepts it with 202.
 *
 * <p>A 400 answer becomes an {@link AuditRejectedException} (do not retry); any other failure becomes an
 * {@link ExternalServiceUnavailableException} (retry later).
 */
@Component
@Profile("!local & !test")
public class RestAuditGateway implements AuditGateway {

    private static final String SERVICE = "MS08";
    private static final int BAD_REQUEST = 400;

    private final RestClient client;
    private final String serviceKey;
    private final String auditPath;

    public RestAuditGateway(
            RestClientFactory factory,
            @Value("${app.gateway.url:http://localhost:8080}") String baseUrl,
            @Value("${app.gateway.service-key:}") String serviceKey,
            @Value("${app.gateway.audit-path:/api/v1/audit/events}") String auditPath) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.gateway.service-key is required (GATEWAY_SERVICE_KEY)");
        }
        this.client = factory.createGateway(baseUrl);
        this.serviceKey = serviceKey.strip();
        this.auditPath = auditPath;
    }

    @Override
    public void send(AuditRecord record) {
        try {
            ExternalCalls.run(SERVICE, () -> client.post()
                    .uri(auditPath)
                    .header(IdentityHeaders.SERVICE_KEY, serviceKey)
                    .header(CorrelationIds.HEADER, record.correlationId())
                    .body(record)
                    .retrieve()
                    .toBodilessEntity());
        } catch (ExternalServiceUnavailableException ex) {
            if (ex.getCause() instanceof RestClientResponseException response
                    && response.getStatusCode().value() == BAD_REQUEST) {
                throw new AuditRejectedException("MS08 rejected the audit fact (HTTP 400)", response);
            }
            throw ex;
        }
    }
}
