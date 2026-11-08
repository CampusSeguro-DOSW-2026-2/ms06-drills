package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.brigade.IncidentIndicators;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInIndicators;
import edu.escuelaing.campusseguro.events.dto.response.EvaluationResponse;
import edu.escuelaing.campusseguro.events.entity.EventEvaluationEntity;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventEvaluationRepository;
import edu.escuelaing.campusseguro.events.service.EventEvaluationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Default implementation of {@link EventEvaluationService}. */
@Service
public class EventEvaluationServiceImpl implements EventEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EventEvaluationServiceImpl.class);

    private final EventEvaluationRepository evaluations;
    private final CheckInGateway checkIn;
    private final BrigadeGateway brigade;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventEvaluationServiceImpl(
            EventEvaluationRepository evaluations,
            CheckInGateway checkIn,
            BrigadeGateway brigade,
            ObjectMapper objectMapper,
            Clock clock) {
        this.evaluations = evaluations;
        this.checkIn = checkIn;
        this.brigade = brigade;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public EvaluationResponse evaluate(UUID eventId) {
        EventEvaluationEntity existing = evaluations.findByEventId(eventId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        EventEvaluationEntity row = new EventEvaluationEntity();
        row.setEventId(eventId);
        row.setEvaluatedAt(clock.instant());
        row.setDetails(write(fill(row, EvaluationDetails.empty(), eventId)));
        return toResponse(evaluations.save(row));
    }

    @Override
    public int completePendingEvaluations() {
        int complete = 0;
        for (EventEvaluationEntity row : evaluations.findByTotalCheckinsIsNullOrTotalIncidentsIsNull()) {
            try {
                EvaluationDetails details = fill(row, read(row.getDetails()), row.getEventId());
                row.setDetails(write(details));
                evaluations.save(row);
                if (isComplete(row)) {
                    complete++;
                }
            } catch (RuntimeException ex) {
                log.error("Could not complete the evaluation of event {}", row.getEventId(), ex);
            }
        }
        return complete;
    }

    /**
     * Asks CHI and BEI only for the numbers the evaluation does not have yet. A service that
     * does not answer is skipped: its numbers stay empty to be completed later.
     */
    private EvaluationDetails fill(EventEvaluationEntity row, EvaluationDetails details, UUID eventId) {
        EvaluationDetails result = details;
        if (row.getTotalCheckins() == null) {
            try {
                CheckInIndicators indicators = checkIn.indicators(eventId);
                row.setTotalCheckins(indicators.totalCheckins());
                row.setCheckinParticipationPct(percentage(indicators.participationPct()));
                result = result.withCheckIn(indicators.updatedAt(), indicators.byStatus());
            } catch (ExternalServiceUnavailableException ex) {
                log.warn("CHI did not answer: the check-in numbers of event {} stay pending", eventId);
            }
        }
        if (row.getTotalIncidents() == null) {
            try {
                IncidentIndicators indicators = brigade.indicators(eventId);
                row.setTotalIncidents(indicators.totalIncidents());
                row.setCasesAttended(indicators.casesAttended());
                row.setCasesResolved(indicators.casesResolved());
                row.setAvgAttentionSeconds(indicators.avgAttentionSeconds());
                result = result.withIncidents(indicators.updatedAt(), indicators.byBrigadier());
            } catch (ExternalServiceUnavailableException ex) {
                log.warn("BEI did not answer: the incident numbers of event {} stay pending", eventId);
            }
        }
        return result;
    }

    private BigDecimal percentage(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isComplete(EventEvaluationEntity row) {
        return row.getTotalCheckins() != null && row.getTotalIncidents() != null;
    }

    private EvaluationResponse toResponse(EventEvaluationEntity row) {
        EvaluationDetails details = read(row.getDetails());
        Map<String, Integer> byStatus = details.checkInByStatus() == null ? Map.of() : details.checkInByStatus();
        List<EvaluationResponse.BrigadierResult> brigadiers = details.brigadiers() == null
                ? List.of()
                : details.brigadiers().stream()
                        .map(item -> new EvaluationResponse.BrigadierResult(
                                item.brigadierId(), item.casesAttended(), item.casesResolved()))
                        .toList();
        boolean noRecords = isComplete(row)
                && row.getTotalCheckins() == 0
                && row.getTotalIncidents() == 0
                && (row.getCasesAttended() == null || row.getCasesAttended() == 0);
        return new EvaluationResponse(
                row.getEventId(), row.getEvaluatedAt(), isComplete(row), noRecords,
                row.getTotalCheckins(), row.getCheckinParticipationPct(), byStatus,
                row.getTotalIncidents(), row.getCasesAttended(), row.getCasesResolved(),
                row.getAvgAttentionSeconds(), brigadiers);
    }

    private String write(EvaluationDetails details) {
        return objectMapper.writeValueAsString(details);
    }

    /** Reads the saved JSON; a missing or unreadable text gives empty details. */
    private EvaluationDetails read(String json) {
        if (json == null || json.isBlank()) {
            return EvaluationDetails.empty();
        }
        try {
            return objectMapper.readValue(json, EvaluationDetails.class);
        } catch (RuntimeException ex) {
            log.warn("The saved evaluation details could not be read", ex);
            return EvaluationDetails.empty();
        }
    }
}
