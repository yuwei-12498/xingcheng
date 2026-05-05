package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.SavedItineraryEditVersion;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItineraryEditVersionService {

    private final SavedItineraryEditVersionRepository savedItineraryEditVersionRepository;
    private final SavedItineraryCodec savedItineraryCodec;

    public ItineraryEditVersionService(SavedItineraryEditVersionRepository savedItineraryEditVersionRepository,
                                       SavedItineraryCodec savedItineraryCodec) {
        this.savedItineraryEditVersionRepository = savedItineraryEditVersionRepository;
        this.savedItineraryCodec = savedItineraryCodec;
    }

    public SavedItineraryEditVersion recordAppliedVersion(Long userId,
                                                          Long itineraryId,
                                                          GenerateReqDTO previousReq,
                                                          ItineraryVO previousSnapshot,
                                                          GenerateReqDTO nextReq,
                                                          ItineraryVO nextSnapshot,
                                                          String source,
                                                          String summary) {
        List<SavedItineraryEditVersion> existing = savedItineraryEditVersionRepository.listByItineraryId(itineraryId);
        int nextVersionNo = existing == null ? 1 : existing.size() + 1;
        if (existing == null || existing.isEmpty()) {
            SavedItineraryEditVersion baseVersion = new SavedItineraryEditVersion();
            baseVersion.setItineraryId(itineraryId);
            baseVersion.setUserId(userId);
            baseVersion.setVersionNo(1);
            baseVersion.setActiveFlag(0);
            baseVersion.setSource("initial");
            baseVersion.setSummary("初始版本");
            baseVersion.setRequestJson(savedItineraryCodec.writeJson(previousReq));
            baseVersion.setItineraryJson(savedItineraryCodec.writeJson(previousSnapshot));
            savedItineraryEditVersionRepository.save(baseVersion);
            nextVersionNo = 2;
        }

        savedItineraryEditVersionRepository.clearActiveFlag(itineraryId);
        SavedItineraryEditVersion nextVersion = new SavedItineraryEditVersion();
        nextVersion.setItineraryId(itineraryId);
        nextVersion.setUserId(userId);
        nextVersion.setVersionNo(nextVersionNo);
        nextVersion.setActiveFlag(1);
        nextVersion.setSource(source);
        nextVersion.setSummary(summary);
        nextVersion.setRequestJson(savedItineraryCodec.writeJson(nextReq));
        nextVersion.setItineraryJson(savedItineraryCodec.writeJson(nextSnapshot));
        return savedItineraryEditVersionRepository.save(nextVersion);
    }
}
