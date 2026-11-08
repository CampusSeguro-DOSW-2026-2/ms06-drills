package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.dto.response.EventTypeResponse;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.service.AuditService;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



/** Tests the type catalog with a mocked repository. */
class EventTypeServiceTest {

    private final EventTypeRepository types = mock(EventTypeRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final EventTypeServiceImpl service = new EventTypeServiceImpl(types, audit);

    private EventTypeEntity type(String code, String name) {
        EventTypeEntity entity = new EventTypeEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setActive(true);
        entity.setCreatedBy("system");
        return entity;
    }

    @Test
    void listsTheActiveTypes() {
        when(types.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(type("SISMO", "Sismo")));

        List<EventTypeResponse> result = service.listActive();

        assertEquals(1, result.size());
        assertEquals("SISMO", result.get(0).code());
    }

    @Test
    void createsAnActiveTypeWithGeneratedCode() {
        when(types.save(any(EventTypeEntity.class))).thenAnswer(call -> call.getArgument(0));

        EventTypeResponse created = service.create("  Fuga de gas ", "coord-1");

        ArgumentCaptor<EventTypeEntity> captor = ArgumentCaptor.forClass(EventTypeEntity.class);
        verify(types).save(captor.capture());
        assertEquals("FUGA_DE_GAS", captor.getValue().getCode());
        assertEquals("Fuga de gas", captor.getValue().getName());
        assertEquals("coord-1", captor.getValue().getCreatedBy());
        assertTrue(created.active());
        verify(audit).record(AuditRecordType.CREATION, "EVENT_TYPE_CREATED", "coord-1", null, "Type Fuga de gas");
    }

    @Test
    void rejectsADuplicatedName() {
        when(types.existsByNameIgnoreCase("Sismo")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create("Sismo", "coord-1"));

        assertEquals(ErrorCode.CONFLICT, ex.code());
        verify(types, never()).save(any(EventTypeEntity.class));
    }

    @Test
    void rejectsADuplicatedGeneratedCode() {
        when(types.existsByCode("INUNDACION")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create("inundacion!", "coord-1"));

        assertEquals(ErrorCode.CONFLICT, ex.code());
    }
}
