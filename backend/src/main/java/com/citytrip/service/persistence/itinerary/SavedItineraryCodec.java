package com.citytrip.service.persistence.itinerary;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class SavedItineraryCodec {

    private final ObjectMapper objectMapper;

    public SavedItineraryCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ItineraryVO deserialize(SavedItinerary entity) {
        try {
            ItineraryVO itinerary = normalizeNodeKeys(readItinerary(entity));
            GenerateReqDTO req = readRequest(entity);
            itinerary.setId(entity.getId());
            itinerary.setCustomTitle(entity.getCustomTitle());
            itinerary.setShareNote(entity.getShareNote());
            itinerary.setOriginalReq(req);
            itinerary.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
            itinerary.setFavoriteTime(entity.getFavoriteTime());
            itinerary.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
            itinerary.setLastSavedAt(entity.getUpdateTime());
            return itinerary;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("反序列化行程失败", ex);
        }
    }

    public GenerateReqDTO readRequest(SavedItinerary entity) throws JsonProcessingException {
        return objectMapper.readValue(entity.getRequestJson(), GenerateReqDTO.class);
    }

    public GenerateReqDTO readRequestJson(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, GenerateReqDTO.class);
    }

    public ItineraryVO readItinerary(SavedItinerary entity) throws JsonProcessingException {
        return normalizeNodeKeys(objectMapper.readValue(entity.getItineraryJson(), ItineraryVO.class));
    }

    public ItineraryVO readItineraryJson(String json) throws JsonProcessingException {
        return normalizeNodeKeys(objectMapper.readValue(json, ItineraryVO.class));
    }

    public String writeJson(Object value) {
        try {
            if (value instanceof ItineraryVO itinerary) {
                return objectMapper.writeValueAsString(normalizeNodeKeys(itinerary));
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("序列化行程失败", ex);
        }
    }

    public String signature(ItineraryVO itinerary) {
        if (itinerary.getNodes() == null || itinerary.getNodes().isEmpty()) {
            return "";
        }
        return itinerary.getNodes().stream()
                .map(ItineraryNodeVO::getPoiId)
                .map(String::valueOf)
                .reduce((left, right) -> left + "-" + right)
                .orElse("");
    }

    public ItineraryVO selectOptionInPlace(ItineraryVO itinerary, String selectedOptionKey) {
        if (itinerary == null) {
            return null;
        }
        normalizeNodeKeys(itinerary);
        if (itinerary.getOptions() == null || itinerary.getOptions().isEmpty()) {
            itinerary.setSelectedOptionKey(null);
            return itinerary;
        }

        ItineraryOptionVO selected = itinerary.getOptions().stream()
                .filter(option -> Objects.equals(option.getOptionKey(), selectedOptionKey))
                .findFirst()
                .orElse(itinerary.getOptions().get(0));

        itinerary.setSelectedOptionKey(selected.getOptionKey());
        itinerary.setNodes(selected.getNodes());
        itinerary.setTotalDuration(selected.getTotalDuration());
        itinerary.setTotalCost(selected.getTotalCost());
        itinerary.setRecommendReason(selected.getRecommendReason());
        itinerary.setRecommendationSource(selected.getRecommendationSource());
        itinerary.setAiDecorated(selected.getAiDecorated());
        itinerary.setAlerts(selected.getAlerts());
        return normalizeNodeKeys(itinerary);
    }

    public ItineraryVO applyEntityMetadata(ItineraryVO itinerary, SavedItinerary entity) {
        if (itinerary == null || entity == null) {
            return itinerary;
        }
        normalizeNodeKeys(itinerary);
        itinerary.setId(entity.getId());
        itinerary.setCustomTitle(entity.getCustomTitle());
        itinerary.setShareNote(entity.getShareNote());
        itinerary.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
        itinerary.setFavoriteTime(entity.getFavoriteTime());
        itinerary.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
        itinerary.setLastSavedAt(entity.getUpdateTime() == null ? LocalDateTime.now() : entity.getUpdateTime());
        return itinerary;
    }

    private ItineraryVO normalizeNodeKeys(ItineraryVO itinerary) {
        if (itinerary == null) {
            return null;
        }
        normalizeNodeKeys(itinerary.getNodes());
        if (itinerary.getOptions() != null) {
            for (ItineraryOptionVO option : itinerary.getOptions()) {
                if (option != null) {
                    normalizeNodeKeys(option.getNodes());
                }
            }
        }
        return itinerary;
    }

    private void normalizeNodeKeys(List<ItineraryNodeVO> nodes) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            ItineraryNodeVO node = nodes.get(i);
            if (node == null || hasText(node.getNodeKey())) {
                continue;
            }
            int dayNo = node.getDayNo() == null ? 0 : node.getDayNo();
            int stepOrder = node.getStepOrder() == null ? (i + 1) : node.getStepOrder();
            String poiPart = node.getPoiId() == null ? "x" : String.valueOf(node.getPoiId());
            node.setNodeKey("node-" + dayNo + "-" + stepOrder + "-" + poiPart);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
