package com.citytrip.service.application.itinerary;

import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.ai.ItineraryAiDecorationService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ItineraryQueryService {

    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final ItinerarySummaryAssembler itinerarySummaryAssembler;
    private final ItineraryAiDecorationService itineraryAiDecorationService;
    private final SavedItineraryCommandService savedItineraryCommandService;

    public ItineraryQueryService(SavedItineraryRepository savedItineraryRepository,
                                 SavedItineraryCodec savedItineraryCodec,
                                 ItinerarySummaryAssembler itinerarySummaryAssembler,
                                 ItineraryAiDecorationService itineraryAiDecorationService,
                                 SavedItineraryCommandService savedItineraryCommandService) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.itinerarySummaryAssembler = itinerarySummaryAssembler;
        this.itineraryAiDecorationService = itineraryAiDecorationService;
        this.savedItineraryCommandService = savedItineraryCommandService;
    }

    public ItineraryVO getLatest(Long userId) {
        SavedItinerary entity = savedItineraryRepository.findLatestOwned(userId);
        return entity == null ? null : refreshTemplateTipIfNeeded(userId, entity);
    }

    public ItineraryVO get(Long userId, Long itineraryId) {
        return refreshTemplateTipIfNeeded(userId, savedItineraryRepository.requireOwned(userId, itineraryId));
    }

    public List<ItinerarySummaryVO> list(Long userId, boolean favoriteOnly, Integer limit) {
        List<SavedItinerary> entities = savedItineraryRepository.listOwned(userId, favoriteOnly, limit);
        List<ItinerarySummaryVO> result = new ArrayList<>(entities.size());
        for (SavedItinerary entity : entities) {
            result.add(toSummary(entity));
        }
        return result;
    }

    public List<ItinerarySummaryVO> listProfile(Long userId, String type, Integer limit) {
        String normalizedType = type == null ? "generated" : type.trim().toLowerCase();
        return switch (normalizedType) {
            case "generated" -> list(userId, false, limit);
            case "saved", "favorite", "favorited" -> list(userId, true, limit);
            default -> throw new BadRequestException("不支持的行程类型，只允许 generated 或 saved");
        };
    }


    private ItineraryVO refreshTemplateTipIfNeeded(Long userId, SavedItinerary entity) {
        ItineraryVO itinerary = savedItineraryCodec.deserialize(entity);
        if (itinerary == null || !isTemplateWarmTip(itinerary.getTips())) {
            return itinerary;
        }
        try {
            GenerateReqDTO req = savedItineraryCodec.readRequest(entity);
            itineraryAiDecorationService.applyWarmTips(itinerary, req);
            return savedItineraryCommandService.save(userId, entity.getId(), req, itinerary);
        } catch (Exception ex) {
            return itinerary;
        }
    }

    private boolean isTemplateWarmTip(String tips) {
        if (!StringUtils.hasText(tips)) {
            return false;
        }
        return tips.contains("\u7CFB\u7EDF\u5DF2\u6309\u5F53\u524D\u65F6\u95F4\u7A97")
                || tips.contains("\u53EF\u6267\u884C\u65B9\u6848")
                || tips.contains("\u5F53\u524D\u9ED8\u8BA4\u65B9\u6848")
                || tips.contains("\u5019\u9009\u8DEF\u7EBF")
                || tips.contains("\u51FA\u884C\u65E5\u671F");
    }

    private ItinerarySummaryVO toSummary(SavedItinerary entity) {
        try {
            GenerateReqDTO req = savedItineraryCodec.readRequest(entity);
            ItineraryVO itinerary = savedItineraryCodec.readItinerary(entity);
            return itinerarySummaryAssembler.toSummary(entity, req, itinerary);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize itinerary summary", ex);
        }
    }
}
