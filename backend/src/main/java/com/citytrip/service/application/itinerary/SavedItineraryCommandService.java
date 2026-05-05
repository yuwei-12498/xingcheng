package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.FavoriteReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.OptionSelectReqDTO;
import com.citytrip.model.dto.PublicStatusReqDTO;
import com.citytrip.model.dto.SaveItineraryReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.application.community.CommunityCacheInvalidationService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class SavedItineraryCommandService {

    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final CommunityCacheInvalidationService communityCacheInvalidationService;

    public SavedItineraryCommandService(SavedItineraryRepository savedItineraryRepository,
                                        SavedItineraryCodec savedItineraryCodec,
                                        CommunityCacheInvalidationService communityCacheInvalidationService) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.communityCacheInvalidationService = communityCacheInvalidationService;
    }

    @Transactional
    public ItineraryVO save(Long userId, Long itineraryId, GenerateReqDTO req, ItineraryVO itinerary) {
        if (itinerary == null) {
            return null;
        }
        itinerary.setOriginalReq(req);
        if (userId == null) {
            return itinerary;
        }

        SavedItinerary entity = savedItineraryRepository.findOwnedForUpdate(userId, itineraryId);
        if (entity == null) {
            entity = new SavedItinerary();
            entity.setUserId(userId);
            entity.setFavorited(0);
            entity.setIsPublic(0);
            entity.setIsDeleted(0);
            entity.setIsGlobalPinned(0);
        }

        preserveStoredMetadata(entity, itinerary);
        applyRequestAndItinerary(entity, req, itinerary);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.evictIfPublic(entity);
        return savedItineraryCodec.applyEntityMetadata(itinerary, entity);
    }

    @Transactional
    public ItineraryVO selectOption(Long userId, Long itineraryId, OptionSelectReqDTO req) {
        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        ItineraryVO itinerary = savedItineraryCodec.deserialize(entity);
        ItineraryVO selected = savedItineraryCodec.selectOptionInPlace(
                itinerary,
                req == null ? null : req.getSelectedOptionKey()
        );

        applyItineraryOnly(entity, selected);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.evictIfPublic(entity);
        return savedItineraryCodec.applyEntityMetadata(selected, entity);
    }

    @Transactional
    public ItineraryVO favorite(Long userId, Long itineraryId, FavoriteReqDTO req) {
        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        ItineraryVO itinerary = savedItineraryCodec.deserialize(entity);
        ItineraryVO selected = savedItineraryCodec.selectOptionInPlace(
                itinerary,
                req == null ? null : req.getSelectedOptionKey()
        );

        String customTitle = normalizeTitle(req == null ? null : req.getTitle());
        selected.setCustomTitle(customTitle);
        entity.setCustomTitle(customTitle);
        entity.setFavorited(1);
        entity.setFavoriteTime(LocalDateTime.now());

        applyItineraryOnly(entity, selected);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.evictIfPublic(entity);
        return savedItineraryCodec.applyEntityMetadata(selected, entity);
    }

    @Transactional
    public ItineraryVO saveFromPublic(Long userId, SaveItineraryReqDTO req) {
        if (userId == null) {
            throw new BadRequestException("Please login before saving a community route");
        }
        if (req == null || req.getSourceItineraryId() == null) {
            throw new BadRequestException("sourceItineraryId is required");
        }

        SavedItinerary source = savedItineraryRepository.requirePublic(req.getSourceItineraryId());
        GenerateReqDTO sourceRequest;
        ItineraryVO sourceItinerary;
        try {
            sourceRequest = savedItineraryCodec.readRequest(source);
            sourceItinerary = savedItineraryCodec.readItinerary(source);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("The community route is corrupted and cannot be saved right now");
        }

        String selectedOptionKey = StringUtils.hasText(req.getSelectedOptionKey())
                ? req.getSelectedOptionKey().trim()
                : sourceItinerary.getSelectedOptionKey();

        ItineraryVO selected = savedItineraryCodec.selectOptionInPlace(sourceItinerary, selectedOptionKey);

        SavedItinerary entity = new SavedItinerary();
        entity.setUserId(userId);
        entity.setFavorited(1);
        entity.setFavoriteTime(LocalDateTime.now());
        entity.setIsPublic(0);
        entity.setIsDeleted(0);
        entity.setIsGlobalPinned(0);

        String customTitle = normalizeTitle(req.getTitle());
        if (!StringUtils.hasText(customTitle)) {
            customTitle = normalizeTitle(source.getCustomTitle());
        }

        selected.setCustomTitle(customTitle);
        selected.setShareNote(source.getShareNote());
        entity.setCustomTitle(customTitle);
        entity.setShareNote(source.getShareNote());

        applyRequestAndItinerary(entity, sourceRequest, selected);
        savedItineraryRepository.saveOrUpdate(entity);
        return savedItineraryCodec.applyEntityMetadata(selected, entity);
    }

    @Transactional
    public void unfavorite(Long userId, Long itineraryId) {
        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        entity.setFavorited(0);
        entity.setFavoriteTime(null);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.evictIfPublic(entity);
    }

    @Transactional
    public ItineraryVO updatePublicStatus(Long userId, Long itineraryId, PublicStatusReqDTO req) {
        if (req == null || req.getIsPublic() == null) {
            throw new BadRequestException("isPublic is required");
        }

        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        ItineraryVO itinerary = savedItineraryCodec.deserialize(entity);
        ItineraryVO selected = savedItineraryCodec.selectOptionInPlace(itinerary, req.getSelectedOptionKey());
        GenerateReqDTO originalReq = selected.getOriginalReq() == null ? new GenerateReqDTO() : selected.getOriginalReq();

        if (Boolean.TRUE.equals(req.getIsPublic())) {
            String customTitle = normalizeTitle(req.getTitle());
            String shareNote = normalizeShareNote(req.getShareNote());
            String coverImageUrl = normalizeCoverImageUrl(req.getCoverImageUrl());
            if (StringUtils.hasText(customTitle)) {
                selected.setCustomTitle(customTitle);
                entity.setCustomTitle(customTitle);
            }
            selected.setShareNote(shareNote);
            selected.setCoverImageUrl(coverImageUrl);
            entity.setShareNote(shareNote);
            originalReq.setThemes(normalizeThemes(req.getThemes(), originalReq.getThemes()));
            selected.setOriginalReq(originalReq);
            entity.setRequestJson(savedItineraryCodec.writeJson(originalReq));
            applyItineraryOnly(entity, selected);
            entity.setIsDeleted(0);
            entity.setDeletedAt(null);
            entity.setDeletedBy(null);
        }

        entity.setIsPublic(Boolean.TRUE.equals(req.getIsPublic()) ? 1 : 0);
        if (!Boolean.TRUE.equals(req.getIsPublic())) {
            entity.setIsGlobalPinned(0);
            entity.setGlobalPinnedAt(null);
            entity.setGlobalPinnedBy(null);
            entity.setPinnedCommentId(null);
        }
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.markDirty();
        return savedItineraryCodec.applyEntityMetadata(selected, entity);
    }

    private void preserveStoredMetadata(SavedItinerary entity, ItineraryVO itinerary) {
        if (StringUtils.hasText(entity.getCustomTitle()) && !StringUtils.hasText(itinerary.getCustomTitle())) {
            itinerary.setCustomTitle(entity.getCustomTitle());
        }
        if (entity.getShareNote() != null && !StringUtils.hasText(itinerary.getShareNote())) {
            itinerary.setShareNote(entity.getShareNote());
        }
        if (!StringUtils.hasText(itinerary.getCoverImageUrl()) && StringUtils.hasText(entity.getItineraryJson())) {
            try {
                ItineraryVO stored = savedItineraryCodec.readItinerary(entity);
                if (stored != null && StringUtils.hasText(stored.getCoverImageUrl())) {
                    itinerary.setCoverImageUrl(stored.getCoverImageUrl().trim());
                }
            } catch (JsonProcessingException ignored) {
                // 保留标题/分享语不依赖旧 JSON 成功解析；封面保留失败时继续正常保存行程。
            }
        }
    }

    private void applyRequestAndItinerary(SavedItinerary entity, GenerateReqDTO req, ItineraryVO itinerary) {
        entity.setRequestJson(savedItineraryCodec.writeJson(req));
        applyItineraryOnly(entity, itinerary);
    }

    private void applyItineraryOnly(SavedItinerary entity, ItineraryVO itinerary) {
        entity.setItineraryJson(savedItineraryCodec.writeJson(itinerary));
        entity.setNodeCount(itinerary.getNodes() == null ? 0 : itinerary.getNodes().size());
        entity.setTotalDuration(itinerary.getTotalDuration());
        entity.setTotalCost(itinerary.getTotalCost());
        entity.setRouteSignature(savedItineraryCodec.signature(itinerary));
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String value = title.trim();
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private String normalizeShareNote(String shareNote) {
        if (!StringUtils.hasText(shareNote)) {
            return null;
        }
        String value = shareNote.trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private String normalizeCoverImageUrl(String coverImageUrl) {
        if (!StringUtils.hasText(coverImageUrl)) {
            return null;
        }
        String value = coverImageUrl.trim();
        if (value.length() > 430_000) {
            throw new BadRequestException("coverImageUrl is too large");
        }
        if ("/community-cover.svg".equals(value)
                || (value.startsWith("/community-covers/") && value.endsWith(".svg"))) {
            return value;
        }
        if (value.matches("^data:image/(png|jpeg|jpg|webp|gif|svg\\+xml);base64,[A-Za-z0-9+/=\\r\\n]+$")) {
            return value;
        }
        throw new BadRequestException("Unsupported cover image format");
    }

    private List<String> normalizeThemes(List<String> themes, List<String> fallbackThemes) {
        List<String> source = (themes == null || themes.isEmpty()) ? fallbackThemes : themes;
        if (source == null) {
            return Collections.emptyList();
        }
        return source.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(3)
                .toList();
    }
}
