package edu.escuelaing.campusseguro.events.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.audit.AuditGateway;
import edu.escuelaing.campusseguro.events.client.audit.AuditRecord;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.PendingAuditEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.PendingAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Tests what is written in the audit outbox and how it is sent to MS08 (mocked repository and gateway). */
class AuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

    private final PendingAuditRepository outbox = mock(PendingAuditRepository.class);
    private final AuditGateway gateway = mock(AuditGateway.class);
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final AuditServiceImpl service =
            new AuditServiceImpl(outbox, gateway, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    private PendingAuditEntity saved() {
        ArgumentCaptor<PendingAuditEntity> captor = ArgumentCaptor.forClass(PendingAuditEntity.class);
        verify(outbox).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> data(PendingAuditEntity row) {
        return mapper.readValue(row.getPayload(), Map.class);
    }

    /** An outbox row as {@link AuditServiceImpl#record} would have saved it. */
    private PendingAuditEntity row(String action, UUID eventId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "CREATION");
        data.put("resource", eventId == null ? "EVENT_TYPE" : "EVENT:" + eventId);
        data.put("result", "SUCCESS");
        data.put("eventName", eventId == null ? null : "Drill");
        data.put("detail", "detail");
        PendingAuditEntity row = new PendingAuditEntity();
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        row.setAction(action);
        row.setActor("coord-1");
        row.setEventId(eventId);
        row.setOccurredAt(NOW.minusSeconds(60));
        row.setPayload(mapper.writeValueAsString(data));
        return row;
    }

    // ---- record ----

    @Test
    void savesAPendingRecordWithTheEventAndTheDetails() {
        EventEntity event = new EventEntity();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(event, "id", id);
        event.setName("Drill");

        service.record(AuditRecordType.CREATION, "EVENT_CREATED", "coord-1", event, "Created as DRAFT");

        PendingAuditEntity row = saved();
        assertEquals("EVENT_CREATED", row.getAction());
        assertEquals("coord-1", row.getActor());
        assertEquals(id, row.getEventId());
        assertEquals(NOW, row.getOccurredAt());
        assertEquals(0, row.getAttempts());
        assertNull(row.getSentAt());
        Map<String, String> data = data(row);
        assertEquals("CREATION", data.get("type"));
        assertEquals("EVENT:" + id, data.get("resource"));
        assertEquals("SUCCESS", data.get("result"));
        assertEquals("Drill", data.get("eventName"));
        assertEquals("Created as DRAFT", data.get("detail"));
    }

    @Test
    void anActionWithoutEventLeavesTheEventEmpty() {
        service.record(AuditRecordType.CREATION, "EVENT_TYPE_CREATED", "coord-1", null, null);

        PendingAuditEntity row = saved();
        assertNull(row.getEventId());
        assertEquals("EVENT_TYPE", data(row).get("resource"));
    }

    @Test
    void longTextsAreCutToTheColumnSize() {
        service.record(AuditRecordType.MODIFICATION, "A".repeat(100), "u".repeat(100), null, "x");

        PendingAuditEntity row = saved();
        assertEquals(60, row.getAction().length());
        assertEquals(64, row.getActor().length());
        assertTrue(row.getPayload().contains("MODIFICATION"));
    }

    // ---- deliverPending ----

    @Test
    void sendsTheRecordsInOrderAndMarksThemSent() {
        UUID eventId = UUID.randomUUID();
        PendingAuditEntity first = row("EVENT_CREATED", eventId);
        PendingAuditEntity second = row("EVENT_TYPE_CREATED", null);
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(first, second));

        int delivered = service.deliverPending();

        assertEquals(2, delivered);
        ArgumentCaptor<AuditRecord> sent = ArgumentCaptor.forClass(AuditRecord.class);
        verify(gateway, times(2)).send(sent.capture());
        AuditRecord record = sent.getAllValues().get(0);
        assertEquals(first.getId(), record.idRegistro());
        assertEquals("CREATION", record.tipoRegistro());
        assertEquals("coord-1", record.usuarioOrigen());
        assertEquals("EVENT_CREATED", record.accion());
        assertEquals(eventId, record.evento().id());
        assertEquals("Drill", record.evento().nombre());
        assertEquals("EVENT:" + eventId, record.recurso());
        assertEquals("SUCCESS", record.resultado());
        assertEquals("MS06", record.origen());
        assertNull(sent.getAllValues().get(1).evento());
        assertEquals(NOW, first.getSentAt());
        assertEquals(1, first.getAttempts());
        assertNotNull(second.getSentAt());
        verify(outbox).save(first);
        verify(outbox).save(second);
    }

    @Test
    void stopsAtTheFirstRecordThatMs08DoesNotAccept() {
        PendingAuditEntity first = row("EVENT_CREATED", UUID.randomUUID());
        PendingAuditEntity second = row("EVENT_UPDATED", UUID.randomUUID());
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(first, second));
        doThrow(new ExternalServiceUnavailableException("MS08", new RuntimeException("down")))
                .when(gateway).send(any());

        int delivered = service.deliverPending();

        assertEquals(0, delivered);
        assertNull(first.getSentAt());
        assertEquals(1, first.getAttempts());
        assertNotNull(first.getLastError());
        verify(outbox).save(first);
        verify(outbox, never()).save(second);
        verify(gateway).send(any());
    }

    @Test
    void aRecordThatFailedBeforeIsSentLaterAndItsErrorIsCleared() {
        PendingAuditEntity row = row("EVENT_CREATED", UUID.randomUUID());
        row.setAttempts(3);
        row.setLastError("MS08 is unavailable");
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(row));
        doNothing().when(gateway).send(any());

        assertEquals(1, service.deliverPending());

        assertEquals(4, row.getAttempts());
        assertNull(row.getLastError());
        assertEquals(NOW, row.getSentAt());
    }

    @Test
    void nothingPendingSendsNothing() {
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of());

        assertEquals(0, service.deliverPending());

        verify(gateway, never()).send(any());
    }
}
