package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.audit.AuditGateway;
import edu.escuelaing.campusseguro.events.client.audit.AuditRecord;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.PendingAuditEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.PendingAuditRepository;
import edu.escuelaing.campusseguro.events.service.AuditService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link AuditService}.
 *
 * <p>The extra data of a record (kind, resource, result, event name, detail) is saved as a small
 * JSON object in the {@code payload} column. No transaction is kept open while MS08 is called:
 * each delivered record is saved on its own after the call.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private static final String ORIGIN = "MS06";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final int ACTION_MAX_LENGTH = 60;
    private static final int ACTOR_MAX_LENGTH = 64;
    private static final int ERROR_MAX_LENGTH = 500;

    private final PendingAuditRepository outbox;
    private final AuditGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditServiceImpl(
            PendingAuditRepository outbox, AuditGateway gateway, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditRecordType type, String action, String actor, EventEntity event, String detail) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type.name());
        data.put("resource", event == null ? "EVENT_TYPE" : "EVENT:" + event.getId());
        data.put("result", RESULT_SUCCESS);
        data.put("eventName", event == null ? null : event.getName());
        data.put("detail", detail);

        PendingAuditEntity row = new PendingAuditEntity();
        row.setAction(cut(action, ACTION_MAX_LENGTH));
        row.setActor(cut(actor, ACTOR_MAX_LENGTH));
        row.setEventId(event == null ? null : event.getId());
        row.setPayload(objectMapper.writeValueAsString(data));
        row.setOccurredAt(clock.instant());
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

    /** Builds the record MS08 receives from an outbox row. */
    @SuppressWarnings("unchecked")
    private AuditRecord toRecord(PendingAuditEntity row) {
        Map<String, String> data = objectMapper.readValue(row.getPayload(), Map.class);
        AuditRecord.EventRef event = row.getEventId() == null
                ? null : new AuditRecord.EventRef(row.getEventId(), data.get("eventName"));
        return new AuditRecord(
                row.getId(), data.get("type"), row.getActor(), row.getAction(), row.getOccurredAt(),
                event, data.get("resource"), data.get("result"), data.get("detail"), ORIGIN);
    }

    private String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
