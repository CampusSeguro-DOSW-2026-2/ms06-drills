package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.CorrelationIds;
import edu.escuelaing.campusseguro.events.client.audit.AuditGateway;
import edu.escuelaing.campusseguro.events.client.audit.AuditRecord;
import edu.escuelaing.campusseguro.events.exception.AuditRejectedException;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.PendingAuditEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.PendingAuditRepository;
import edu.escuelaing.campusseguro.events.service.AuditExtras;
import edu.escuelaing.campusseguro.events.service.AuditService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link AuditService}.
 *
 * <p>The extra data of a fact (kind of record, resource, result, correlation id, kind of event, detail, attributes)
 * is saved as a small JSON object in the {@code payload} column, so the table does not change. No
 * transaction is kept open while MS08 is called: each delivered record is saved on its own after the call.
 *
 * <p>Rows saved before the MS08 contract existed have no {@code correlation_id} in their payload; they are
 * translated on the fly (see {@link #toRecord}).
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private static final String MODULE = "MS06";
    private static final String RESULT_SUCCESS = "EXITO";
    private static final String KIND_DRILL = "SIMULACRO";
    private static final String KIND_REAL = "REAL";
    private static final String RESOURCE_EVENT = "evento:";
    private static final String LEGACY_RESOURCE_EVENT = "EVENT:";
    private static final String REJECTED_PREFIX = "REJECTED: ";
    private static final int ACTION_MAX_LENGTH = 60;
    private static final int ACTOR_MAX_LENGTH = 64;
    private static final int DETAIL_MAX_LENGTH = 100;
    private static final int ERROR_MAX_LENGTH = 500;
    private static final Pattern UUID_TEXT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final PendingAuditRepository outbox;
    private final EventRepository events;
    private final AuditGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditServiceImpl(
            PendingAuditRepository outbox,
            EventRepository events,
            AuditGateway gateway,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outbox = outbox;
        this.events = events;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditRecordType type, String action, String actor, EventEntity event, String detail,
            AuditExtras extras) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type.name());
        data.put("resource", extras.resource() != null ? extras.resource()
                : (event == null ? null : RESOURCE_EVENT + event.getId()));
        data.put("attributes", extras.attributes());
        data.put("result", RESULT_SUCCESS);
        data.put("correlation_id", CorrelationIds.current());
        data.put("event_kind", event == null ? null : (event.isSimulacro() ? KIND_DRILL : KIND_REAL));
        data.put("detail", cut(detail, DETAIL_MAX_LENGTH));

        PendingAuditEntity row = new PendingAuditEntity();
        row.setAction(cut(action, ACTION_MAX_LENGTH));
        row.setActor(cut(actor, ACTOR_MAX_LENGTH));
        row.setEventId(event == null ? null : event.getId());
        row.setPayload(objectMapper.writeValueAsString(data));
        row.setOccurredAt(extras.occurredAt() != null ? extras.occurredAt() : clock.instant());
        row.setAttempts(0);
        outbox.save(row);
    }

    @Override
    public int deliverPending() {
        List<PendingAuditEntity> pending = outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc();
        int delivered = 0;
        for (PendingAuditEntity row : pending) {
            try {
                gateway.send(toRecord(row));
            } catch (AuditRejectedException ex) {
                // MS08 said 400: the same content would fail again. Close the row (with the reason) so it
                // does not block the ones behind it.
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(cut(REJECTED_PREFIX + ex.getMessage(), ERROR_MAX_LENGTH));
                row.setSentAt(clock.instant());
                outbox.save(row);
                log.error("MS08 rejected audit record {} (action {}); it will not be sent again",
                        row.getId(), row.getAction());
                continue;
            } catch (ExternalServiceUnavailableException ex) {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(cut(ex.getMessage(), ERROR_MAX_LENGTH));
                outbox.save(row);
                log.warn("MS08 did not accept audit record {}; {} records stay pending",
                        row.getId(), pending.size() - delivered);
                break;
            }
            row.setSentAt(clock.instant());
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(null);
            outbox.save(row);
            delivered++;
        }
        return delivered;
    }

    /** Builds the fact MS08 receives from an outbox row. */
    private AuditRecord toRecord(PendingAuditEntity row) {
        Map<?, ?> data = objectMapper.readValue(row.getPayload(), Map.class);
        boolean legacy = !data.containsKey("correlation_id");
        String eventId = row.getEventId() == null ? null : row.getEventId().toString();
        return new AuditRecord(
                row.getId().toString(),
                isUuid(row.getActor()) ? row.getActor() : null,
                MODULE,
                row.getAction(),
                legacy ? legacyResource(text(data.get("resource"))) : text(data.get("resource")),
                legacy ? RESULT_SUCCESS : text(data.get("result")),
                row.getOccurredAt().toString(),
                legacy ? row.getId().toString() : text(data.get("correlation_id")),
                legacy ? null : text(data.get("detail")),
                eventId,
                eventId == null ? null : (legacy ? kindOf(row.getEventId()) : text(data.get("event_kind"))),
                legacy ? null : attributesOf(data.get("attributes")));
    }

    /** Attributes saved with the record, or {@code null} when it has none. */
    private Map<String, Object> attributesOf(Object saved) {
        if (!(saved instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        map.forEach((key, value) -> attributes.put(String.valueOf(key), value));
        return attributes;
    }

    /** Kind of an event saved before the payload had it; REAL if the event is not found. */
    private String kindOf(UUID eventId) {
        return events.findById(eventId)
                .map(event -> event.isSimulacro() ? KIND_DRILL : KIND_REAL)
                .orElse(KIND_REAL);
    }

    /** {@code EVENT:{id}} (old format) becomes {@code evento:{id}}; anything else has no resource. */
    private String legacyResource(String resource) {
        if (resource != null && resource.startsWith(LEGACY_RESOURCE_EVENT)) {
            return RESOURCE_EVENT + resource.substring(LEGACY_RESOURCE_EVENT.length());
        }
        return null;
    }

    private boolean isUuid(String value) {
        return value != null && UUID_TEXT.matcher(value).matches();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
