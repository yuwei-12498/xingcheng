package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.entity.UserCustomPoi;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class UserCustomPoiCommandService {

    private final UserCustomPoiRepository userCustomPoiRepository;
    private final PlaceDisambiguationService placeDisambiguationService;

    public UserCustomPoiCommandService(UserCustomPoiRepository userCustomPoiRepository,
                                       PlaceDisambiguationService placeDisambiguationService) {
        this.userCustomPoiRepository = userCustomPoiRepository;
        this.placeDisambiguationService = placeDisambiguationService;
    }

    public UserCustomPoi resolveForInsertion(Long userId, String cityName, ItineraryEditOperationDTO operation) {
        if (operation != null && operation.getCustomPoiId() != null) {
            return userCustomPoiRepository.requireOwned(userId, operation.getCustomPoiId());
        }

        ItineraryEditOperationDTO.CustomPoiDraft draft = operation == null ? null : operation.getCustomPoiDraft();
        if (draft == null || !StringUtils.hasText(draft.getName()) || !StringUtils.hasText(draft.getRoughLocation())) {
            throw new BadRequestException("请先填写自定义地点名称和粗略位置");
        }

        if (hasResolvedCoordinates(draft)) {
            UserCustomPoi entity = new UserCustomPoi();
            entity.setUserId(userId);
            entity.setCityName(cityName);
            entity.setName(draft.getName().trim());
            entity.setRoughLocation(draft.getRoughLocation().trim());
            entity.setCategory(normalizeText(draft.getCategory()));
            entity.setReason(normalizeText(draft.getReason()));
            entity.setAddress(StringUtils.hasText(draft.getAddress()) ? draft.getAddress().trim() : draft.getRoughLocation().trim());
            entity.setDistrict(normalizeText(draft.getDistrict()));
            entity.setLatitude(draft.getLatitude());
            entity.setLongitude(draft.getLongitude());
            entity.setSuggestedStayDuration(operation != null && operation.getStayDuration() != null
                    ? operation.getStayDuration()
                    : draft.getStayDuration());
            entity.setGeoSource(normalizeText(draft.getGeoSource()));
            return userCustomPoiRepository.save(entity);
        }

        String keyword = draft.getName().trim() + " " + draft.getRoughLocation().trim();
        PlaceDisambiguationService.ResolvedPlace resolvedPlace = placeDisambiguationService
                .resolveBest(keyword, cityName, normalizeText(draft.getCategory()))
                .orElseThrow(() -> new BadRequestException("联网搜索失败，出于用户注意安全，请详细填写"));

        UserCustomPoi entity = new UserCustomPoi();
        entity.setUserId(userId);
        entity.setCityName(cityName);
        entity.setName(resolvedPlace.canonicalName());
        entity.setRoughLocation(draft.getRoughLocation().trim());
        entity.setCategory(normalizeText(draft.getCategory()));
        entity.setReason(normalizeText(draft.getReason()));
        entity.setAddress(resolvedPlace.canonicalName());
        entity.setDistrict(resolvedPlace.district());
        entity.setLatitude(resolvedPlace.latitude());
        entity.setLongitude(resolvedPlace.longitude());
        entity.setSuggestedStayDuration(draft.getStayDuration());
        entity.setGeoSource(resolvedPlace.source());
        return userCustomPoiRepository.save(entity);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean hasResolvedCoordinates(ItineraryEditOperationDTO.CustomPoiDraft draft) {
        return isValidCoordinate(draft.getLatitude()) && isValidCoordinate(draft.getLongitude());
    }

    private boolean isValidCoordinate(BigDecimal value) {
        return value != null;
    }
}
