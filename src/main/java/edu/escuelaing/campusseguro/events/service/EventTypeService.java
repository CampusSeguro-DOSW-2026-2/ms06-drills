package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.EventTypeResponse;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Event type catalog: lists the types and adds new ones. */
@Service
public class EventTypeService {

    private final EventTypeRepository types;

    public EventTypeService(EventTypeRepository types) {
        this.types = types;
    }

    /** Active types sorted by name. */
    @Transactional(readOnly = true)
    public List<EventTypeResponse> listActive() {
        return types.findByActiveTrueOrderByNameAsc().stream().map(EventTypeResponse::from).toList();
    }

    /**
     * Adds a type. The code is generated from the name.
     *
     * @param name  display name
     * @param actor user who adds it
     * @throws BusinessException (CONFLICT) if the name or the generated code already exists
     */
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
        return EventTypeResponse.from(types.save(type));
    }
}
