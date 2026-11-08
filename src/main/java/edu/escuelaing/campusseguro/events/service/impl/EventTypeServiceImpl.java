package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.dto.response.EventTypeResponse;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.service.EventTypeCodes;
import edu.escuelaing.campusseguro.events.service.EventTypeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default implementation of {@link EventTypeService}. */
@Service
public class EventTypeServiceImpl implements EventTypeService {

    private final EventTypeRepository types;

    public EventTypeServiceImpl(EventTypeRepository types) {
        this.types = types;
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
        return EventTypeResponse.from(types.save(type));
    }
}
