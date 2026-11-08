package edu.escuelaing.campusseguro.events.client.audit;

import edu.escuelaing.campusseguro.events.client.ExternalCalls;
import edu.escuelaing.campusseguro.events.client.RestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Real {@link AuditGateway}. It posts each record to MS08 ({@code POST /api/v1/auditoria/registros}). */
@Component
@Profile("!local & !test")
public class RestAuditGateway implements AuditGateway {

    private static final String SERVICE = "MS08";

    private final RestClient client;
    private final String recordsPath;

    public RestAuditGateway(
            RestClientFactory factory,
            @Value("${app.clients.ms08-url:http://localhost:8088}") String baseUrl,
            @Value("${app.clients.ms08.records-path:/api/v1/auditoria/registros}") String recordsPath) {
        this.client = factory.create(baseUrl);
        this.recordsPath = recordsPath;
    }

    @Override
    public void send(AuditRecord record) {
        ExternalCalls.run(SERVICE, () -> client.post()
                .uri(recordsPath)
                .body(record)
                .retrieve()
                .toBodilessEntity());
    }
}
