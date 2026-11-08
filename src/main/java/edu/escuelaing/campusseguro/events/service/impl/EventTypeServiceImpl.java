package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.dto.response.EventTypeResponse;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.service.EventTypeCodes;
import edu.escuelaing.campusseguro.events.service.AuditService;
import edu.escuelaing.campusseguro.events.service.EventTypeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default implementation of {@link EventTypeService}. */
@Service
public class EventTypeServiceImpl implements EventTypeService {

    private final EventTypeRepository types;
    private final AuditService audit;

    public EventTypeServiceImpl(EventTypeRepository types, AuditService audit) {
        this.types = types;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventTypeResponse> listActive() {
        return types.findByActiveTrueOrderByNameAsc().stream().map(EventTypeResponse::from).toList();
    }

    @Override
    @Transactional
    public EventTypeResponse create(String name, String actor) {
        String cleanName = name.strip();
        String code = EventTypeCodes.fromName(cleanName);
        if (types.existsByNameIgnoreCase(cleanName) || types.existsByCode(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "An event type like '" + cleanName + "' already exists");
        }
        EventTypeEntity type = new EventTypeEntity();
        type.setCode(code);
        type.setName(cleanName);
        type.setActive(true);
        type.setCreatedBy(actor);
        EventTypeEntity saved = types.save(type);
        audit.record(AuditRecordType.CREATION, "EVENT_TYPE_CREATED", actor, null, "Type " + saved.getName());
        return EventTypeResponse.from(saved);
    }
}
