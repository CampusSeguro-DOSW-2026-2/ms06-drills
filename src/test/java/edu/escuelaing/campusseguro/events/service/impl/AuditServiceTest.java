package edu.escuelaing.campusseguro.events.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final EventRepository events = mock(EventRepository.class);
    private final AuditGateway gateway = mock(AuditGateway.class);
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final AuditServiceImpl service =
            new AuditServiceImpl(outbox, events, gateway, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    private PendingAuditEntity saved() {
        ArgumentCaptor<PendingAuditEntity> captor = ArgumentCaptor.forClass(PendingAuditEntity.class);
        verify(outbox).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(PendingAuditEntity row) {
        return mapper.readValue(row.getPayload(), Map.class);
    }

    /** An outbox row as {@link AuditServiceImpl#record} saves it now (MS08 contract). */
    private PendingAuditEntity row(String action, UUID eventId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "CREATION");
        data.put("resource", eventId == null ? null : "evento:" + eventId);
        data.put("result", "EXITO");
        data.put("correlation_id", "corr-1");
        data.put("event_kind", eventId == null ? null : "SIMULACRO");
        data.put("detail", "CREATED_AS_DRAFT");
        data.put("attributes", eventId == null ? null : Map.of("estado_anterior", "DRAFT", "duracion_segundos", 600));
        return rowWith(action, eventId, "11111111-1111-4111-8111-111111111111", data);
    }

    /** A row written before the MS08 contract: old keys, no correlation id, actor that is not a UUID. */
    private PendingAuditEntity legacyRow(UUID eventId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "CREATION");
        data.put("resource", eventId == null ? "EVENT_TYPE" : "EVENT:" + eventId);
        data.put("result", "SUCCESS");
        data.put("eventName", "Drill");
        data.put("detail", "DRAFT -> SCHEDULED: free text reason");
        return rowWith("EVENT_CREATED", eventId, "coord-1", data);
    }

    private PendingAuditEntity rowWith(String action, UUID eventId, String actor, Map<String, Object> data) {
        PendingAuditEntity row = new PendingAuditEntity();
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        row.setAction(action);
        row.setActor(actor);
        row.setEventId(eventId);
        row.setOccurredAt(NOW.minusSeconds(60));
        row.setPayload(mapper.writeValueAsString(data));
        return row;
    }

    // ---- record ----

    private EventEntity event(boolean drill) {
        EventEntity event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setName("Drill");
        event.setSimulacro(drill);
        return event;
    }

    @Test
    void savesAPendingRecordWithTheMs08Data() {
        EventEntity event = event(true);

        service.record(AuditRecordType.CREATION, "EVENT_CREATED", "coord-1", event, "CREATED_AS_DRAFT");

        PendingAuditEntity row = saved();
        assertEquals("EVENT_CREATED", row.getAction());
        assertEquals("coord-1", row.getActor());
        assertEquals(event.getId(), row.getEventId());
        assertEquals(NOW, row.getOccurredAt());
        assertEquals(0, row.getAttempts());
        assertNull(row.getSentAt());
        Map<String, Object> data = data(row);
        assertEquals("CREATION", data.get("type"));
        assertEquals("evento:" + event.getId(), data.get("resource"));
        assertEquals("EXITO", data.get("result"));
        assertEquals("SIMULACRO", data.get("event_kind"));
        assertEquals("CREATED_AS_DRAFT", data.get("detail"));
        assertNotNull(data.get("correlation_id"));
        assertFalse(data.containsKey("eventName"), "the event name is not sent to MS08");
    }

    @Test
    void theCatalogExtrasAreSaved() {
        EventEntity event = event(true);
        Instant startedAt = NOW.minusSeconds(5);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("automatico", true);
        attributes.put("duracion_segundos", 600L);

        service.record(AuditRecordType.MODIFICATION, "EVENT_STARTED", "system", event, null,
                new AuditExtras("evento:custom", attributes, startedAt));

        PendingAuditEntity row = saved();
        assertEquals(startedAt, row.getOccurredAt(), "the real time of the action is kept");
        assertEquals("evento:custom", data(row).get("resource"));
        assertEquals(Map.of("automatico", true, "duracion_segundos", 600), data(row).get("attributes"));
    }

    @Test
    void anEventTypeKeepsItsOwnResourceWithoutEvent() {
        service.record(AuditRecordType.CREATION, "EVENT_TYPE_CREATED", "coord-1", null, null,
                new AuditExtras("tipo_evento:abc", null, null));

        PendingAuditEntity row = saved();
        assertNull(row.getEventId());
        assertEquals("tipo_evento:abc", data(row).get("resource"));
        assertEquals(NOW, row.getOccurredAt());
    }

    @Test
    void aRealEventIsSavedAsReal() {
        service.record(AuditRecordType.MODIFICATION, "EVENT_STARTED", "system", event(false), "SCHEDULED_TO_IN_PROGRESS");

        assertEquals("REAL", data(saved()).get("event_kind"));
    }

    @Test
    void theCorrelationIdOfTheRequestIsKept() {
        CorrelationIds.set("corr-from-gateway");
        try {
            service.record(AuditRecordType.MODIFICATION, "EVENT_UPDATED", "coord-1", event(true), "EVENT_EDITED");
        } finally {
            CorrelationIds.clear();
        }

        assertEquals("corr-from-gateway", data(saved()).get("correlation_id"));
    }

    @Test
    void anActionWithoutEventLeavesTheEventAndTheResourceEmpty() {
        service.record(AuditRecordType.CREATION, "EVENT_TYPE_CREATED", "coord-1", null, null);

        PendingAuditEntity row = saved();
        assertNull(row.getEventId());
        assertNull(data(row).get("resource"));
        assertNull(data(row).get("event_kind"));
    }

    @Test
    void longTextsAreCutToTheColumnAndContractSize() {
        service.record(AuditRecordType.MODIFICATION, "A".repeat(100), "u".repeat(100), null, "d".repeat(300));

        PendingAuditEntity row = saved();
        assertEquals(60, row.getAction().length());
        assertEquals(64, row.getActor().length());
        assertEquals(100, ((String) data(row).get("detail")).length());
        assertTrue(row.getPayload().contains("MODIFICATION"));
    }

    // ---- deliverPending ----

    @Test
    void sendsTheFactsInOrderWithTheMs08ContractAndMarksThemSent() {
        UUID eventId = UUID.randomUUID();
        PendingAuditEntity first = row("EVENT_CREATED", eventId);
        PendingAuditEntity second = row("EVENT_TYPE_CREATED", null);
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(first, second));

        int delivered = service.deliverPending();

        assertEquals(2, delivered);
        ArgumentCaptor<AuditRecord> sent = ArgumentCaptor.forClass(AuditRecord.class);
        verify(gateway, times(2)).send(sent.capture());
        AuditRecord record = sent.getAllValues().get(0);
        assertEquals(first.getId().toString(), record.id());
        assertEquals("11111111-1111-4111-8111-111111111111", record.usuarioId());
        assertEquals("MS06", record.modulo());
        assertEquals("EVENT_CREATED", record.accion());
        assertEquals("evento:" + eventId, record.recursoAfectado());
        assertEquals("EXITO", record.resultado());
        assertEquals(NOW.minusSeconds(60).toString(), record.fechaHora());
        assertEquals("corr-1", record.correlationId());
        assertEquals("CREATED_AS_DRAFT", record.detalle());
        assertEquals(eventId.toString(), record.eventoId());
        assertEquals("SIMULACRO", record.tipoEvento());
        assertEquals(Map.of("estado_anterior", "DRAFT", "duracion_segundos", 600), record.atributos());
        AuditRecord withoutEvent = sent.getAllValues().get(1);
        assertNull(withoutEvent.atributos());
        assertNull(withoutEvent.eventoId());
        assertNull(withoutEvent.tipoEvento());
        assertNull(withoutEvent.recursoAfectado());
        assertEquals(NOW, first.getSentAt());
        assertEquals(1, first.getAttempts());
        assertNotNull(second.getSentAt());
        verify(outbox).save(first);
        verify(outbox).save(second);
    }

    @Test
    void anActorThatIsNotAUuidIsSentWithoutUser() {
        PendingAuditEntity system = row("EVENT_STARTED", UUID.randomUUID());
        system.setActor("system");
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(system));

        service.deliverPending();

        ArgumentCaptor<AuditRecord> sent = ArgumentCaptor.forClass(AuditRecord.class);
        verify(gateway).send(sent.capture());
        assertNull(sent.getValue().usuarioId());
    }

    @Test
    void aRowSavedBeforeTheContractIsTranslated() {
        UUID eventId = UUID.randomUUID();
        EventEntity stored = event(true);
        when(events.findById(eventId)).thenReturn(Optional.of(stored));
        PendingAuditEntity old = legacyRow(eventId);
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(old));

        assertEquals(1, service.deliverPending());

        ArgumentCaptor<AuditRecord> sent = ArgumentCaptor.forClass(AuditRecord.class);
        verify(gateway).send(sent.capture());
        AuditRecord record = sent.getValue();
        assertEquals("evento:" + eventId, record.recursoAfectado());
        assertEquals("EXITO", record.resultado());
        assertEquals(old.getId().toString(), record.correlationId());
        assertNull(record.detalle(), "the old free-text detail is not sent");
        assertNull(record.usuarioId());
        assertNull(record.atributos());
        assertEquals("SIMULACRO", record.tipoEvento());
    }

    @Test
    void aLegacyEventThatNoLongerExistsIsSentAsReal() {
        UUID eventId = UUID.randomUUID();
        when(events.findById(eventId)).thenReturn(Optional.empty());
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(legacyRow(eventId)));

        service.deliverPending();

        ArgumentCaptor<AuditRecord> sent = ArgumentCaptor.forClass(AuditRecord.class);
        verify(gateway).send(sent.capture());
        assertEquals("REAL", sent.getValue().tipoEvento());
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
    void aRecordThatMs08RejectsIsClosedAndDoesNotBlockTheNextOnes() {
        PendingAuditEntity bad = row("EVENT_CREATED", UUID.randomUUID());
        PendingAuditEntity good = row("EVENT_UPDATED", UUID.randomUUID());
        when(outbox.findTop100BySentAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(bad, good));
        doThrow(new AuditRejectedException("MS08 rejected the audit fact (HTTP 400)", new RuntimeException("400")))
                .doNothing()
                .when(gateway).send(any());

        int delivered = service.deliverPending();

        assertEquals(1, delivered);
        assertEquals(NOW, bad.getSentAt(), "closed so it is not read again");
        assertTrue(bad.getLastError().startsWith("REJECTED"));
        assertEquals(1, bad.getAttempts());
        assertNotNull(good.getSentAt());
        assertNull(good.getLastError());
        verify(gateway, times(2)).send(any());
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
