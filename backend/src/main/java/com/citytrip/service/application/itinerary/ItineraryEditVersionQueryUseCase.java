package com.citytrip.service.application.itinerary;

import com.citytrip.model.vo.ItineraryEditVersionVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItineraryEditVersionQueryUseCase {

    private final SavedItineraryEditVersionRepository savedItineraryEditVersionRepository;

    public ItineraryEditVersionQueryUseCase(SavedItineraryEditVersionRepository savedItineraryEditVersionRepository) {
        this.savedItineraryEditVersionRepository = savedItineraryEditVersionRepository;
    }

    public List<ItineraryEditVersionVO> list(Long userId, Long itineraryId) {
        return savedItineraryEditVersionRepository.listByItineraryId(itineraryId).stream()
                .filter(item -> item != null && java.util.Objects.equals(item.getUserId(), userId))
                .map(item -> {
                    ItineraryEditVersionVO vo = new ItineraryEditVersionVO();
                    vo.setId(item.getId());
                    vo.setVersionNo(item.getVersionNo());
                    vo.setSource(item.getSource());
                    vo.setSummary(item.getSummary());
                    vo.setActive(java.util.Objects.equals(item.getActiveFlag(), 1));
                    vo.setCreateTime(item.getCreateTime());
                    return vo;
                })
                .toList();
    }
}
