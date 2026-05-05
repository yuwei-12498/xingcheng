package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ItineraryEditApplyReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ItineraryEditApplyUseCase {

    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final ItineraryEditPlannerService itineraryEditPlannerService;
    private final SavedItineraryCommandService savedItineraryCommandService;
    private final ItineraryEditVersionService itineraryEditVersionService;

    public ItineraryEditApplyUseCase(SavedItineraryRepository savedItineraryRepository,
                                     SavedItineraryCodec savedItineraryCodec,
                                     ItineraryEditPlannerService itineraryEditPlannerService,
                                     SavedItineraryCommandService savedItineraryCommandService,
                                     ItineraryEditVersionService itineraryEditVersionService) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.itineraryEditPlannerService = itineraryEditPlannerService;
        this.savedItineraryCommandService = savedItineraryCommandService;
        this.itineraryEditVersionService = itineraryEditVersionService;
    }

    @Transactional
    public ItineraryVO apply(Long userId, Long itineraryId, ItineraryEditApplyReqDTO req) {
        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        GenerateReqDTO storedReq = readRequest(entity);
        ItineraryVO storedItinerary = readItinerary(entity);
        ItineraryEditPlannerService.PreparedEdit prepared = itineraryEditPlannerService.prepare(userId, storedItinerary, storedReq, req);
        ItineraryVO saved = savedItineraryCommandService.save(userId, itineraryId, prepared.updatedRequest(), prepared.itinerary());
        String source = StringUtils.hasText(req == null ? null : req.getSource()) ? req.getSource().trim() : "form";
        String summary = StringUtils.hasText(req == null ? null : req.getSummary()) ? req.getSummary().trim() : prepared.summary();
        saved.setActiveEditVersionId(itineraryEditVersionService.recordAppliedVersion(
                userId,
                itineraryId,
                storedReq,
                storedItinerary,
                prepared.updatedRequest(),
                saved,
                source,
                summary
        ).getId());
        return saved;
    }

    private GenerateReqDTO readRequest(SavedItinerary entity) {
        try {
            return savedItineraryCodec.readRequest(entity);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("读取原始行程请求失败", ex);
        }
    }

    private ItineraryVO readItinerary(SavedItinerary entity) {
        try {
            return savedItineraryCodec.readItinerary(entity);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("读取当前行程快照失败", ex);
        }
    }
}
