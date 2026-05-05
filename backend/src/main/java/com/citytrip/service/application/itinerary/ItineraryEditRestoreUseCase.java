package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ItineraryEditRestoreReqDTO;
import com.citytrip.model.entity.SavedItineraryEditVersion;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryEditRestoreUseCase {

    private final SavedItineraryEditVersionRepository savedItineraryEditVersionRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final SavedItineraryCommandService savedItineraryCommandService;

    public ItineraryEditRestoreUseCase(SavedItineraryEditVersionRepository savedItineraryEditVersionRepository,
                                       SavedItineraryCodec savedItineraryCodec,
                                       SavedItineraryCommandService savedItineraryCommandService) {
        this.savedItineraryEditVersionRepository = savedItineraryEditVersionRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.savedItineraryCommandService = savedItineraryCommandService;
    }

    @Transactional
    public ItineraryVO restore(Long userId, Long itineraryId, ItineraryEditRestoreReqDTO req) {
        SavedItineraryEditVersion version = savedItineraryEditVersionRepository.requireOwnedVersion(
                userId,
                itineraryId,
                req == null ? null : req.getVersionId()
        );
        GenerateReqDTO restoredReq = readRequest(version.getRequestJson());
        ItineraryVO restoredItinerary = readItinerary(version.getItineraryJson());
        ItineraryVO saved = savedItineraryCommandService.save(userId, itineraryId, restoredReq, restoredItinerary);
        savedItineraryEditVersionRepository.clearActiveFlag(itineraryId);
        savedItineraryEditVersionRepository.markActive(version.getId());
        saved.setActiveEditVersionId(version.getId());
        return saved;
    }

    private GenerateReqDTO readRequest(String json) {
        try {
            return savedItineraryCodec.readRequestJson(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("读取版本请求快照失败", ex);
        }
    }

    private ItineraryVO readItinerary(String json) {
        try {
            return savedItineraryCodec.readItineraryJson(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("读取版本行程快照失败", ex);
        }
    }
}
