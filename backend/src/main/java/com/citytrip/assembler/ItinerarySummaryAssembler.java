package com.citytrip.assembler;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class ItinerarySummaryAssembler {

    private static final String DEFAULT_COVER_IMAGE_URL = "/community-covers/v2/cover-citywalk.svg";
    private static final String LEGACY_DEFAULT_COVER_IMAGE_URL = "/community-cover.svg";
    private static final String LEGACY_PRESET_PREFIX = "/community-covers/";
    private static final String VERSIONED_PRESET_PREFIX = "/community-covers/v2/";
    private static final List<String> PRESET_COVER_IMAGE_URLS = List.of(
            "/community-covers/v2/cover-citywalk.svg",
            "/community-covers/v2/cover-food.svg",
            "/community-covers/v2/cover-night.svg",
            "/community-covers/v2/cover-nature.svg",
            "/community-covers/v2/cover-culture.svg",
            "/community-covers/v2/cover-shopping.svg"
    );

    public ItinerarySummaryVO toSummary(SavedItinerary entity, GenerateReqDTO req, ItineraryVO itinerary) {
        ItinerarySummaryVO summary = new ItinerarySummaryVO();
        summary.setId(entity.getId());
        summary.setTitle(buildTitle(req, itinerary));
        summary.setCityName(req == null ? null : req.getCityName());
        summary.setRouteSummary(buildRouteSummary(itinerary));
        summary.setCoverImageUrl(resolveCoverImage(itinerary));
        summary.setTripDate(req == null ? null : req.getTripDate());
        summary.setStartTime(req == null ? null : req.getStartTime());
        summary.setEndTime(req == null ? null : req.getEndTime());
        summary.setNodeCount(entity.getNodeCount());
        summary.setTotalDuration(entity.getTotalDuration());
        summary.setTotalCost(entity.getTotalCost());
        summary.setBudgetLevel(req == null ? null : req.getBudgetLevel());
        summary.setCompanionType(req == null ? null : req.getCompanionType());
        summary.setRainy(req != null && Boolean.TRUE.equals(req.getIsRainy()));
        summary.setNight(req != null && Boolean.TRUE.equals(req.getIsNight()));
        summary.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
        summary.setFavoriteTime(entity.getFavoriteTime());
        summary.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
        summary.setUpdatedAt(entity.getUpdateTime());
        summary.setThemes(req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes());

        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null
                ? Collections.emptyList()
                : itinerary.getNodes();
        if (!nodes.isEmpty()) {
            summary.setFirstPoiName(nodes.get(0).getPoiName());
            summary.setLastPoiName(nodes.get(nodes.size() - 1).getPoiName());
        }
        return summary;
    }

    public CommunityItineraryVO toCommunitySummary(SavedItinerary entity,
                                                   User author,
                                                   GenerateReqDTO req,
                                                   ItineraryVO itinerary,
                                                   Long commentCount,
                                                   Long likeCount) {
        CommunityItineraryVO summary = new CommunityItineraryVO();
        summary.setId(entity.getId());
        summary.setTitle(buildTitle(req, itinerary));
        summary.setCityName(req == null ? null : req.getCityName());
        summary.setTripDate(req == null ? null : req.getTripDate());
        summary.setCoverImageUrl(resolveCoverImage(itinerary));
        summary.setTotalDuration(entity.getTotalDuration());
        summary.setTotalCost(entity.getTotalCost());
        summary.setNodeCount(entity.getNodeCount());
        summary.setUpdatedAt(entity.getUpdateTime());
        summary.setAuthorLabel(resolveAuthorLabel(author));
        summary.setShareNote(resolveShareNote(entity, itinerary));
        summary.setRouteSummary(buildRouteSummary(itinerary));
        summary.setThemes(req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes());
        summary.setHighlights(resolveHighlights(itinerary));
        summary.setLikeCount(likeCount == null ? 0L : likeCount);
        summary.setLiked(false);
        summary.setCommentCount(commentCount == null ? 0L : commentCount);
        return summary;
    }

    public String resolveAuthorLabel(User author) {
        if (author == null) {
            return "匿名用户";
        }
        if (StringUtils.hasText(author.getNickname())) {
            return author.getNickname().trim();
        }
        if (StringUtils.hasText(author.getUsername())) {
            return author.getUsername().trim();
        }
        return "注册用户";
    }

    public String buildRouteSummary(ItineraryVO itinerary) {
        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null
                ? Collections.emptyList()
                : itinerary.getNodes();
        if (nodes.isEmpty()) {
            return "暂无行程节点";
        }
        return nodes.get(0).getPoiName() + " -> " + nodes.get(nodes.size() - 1).getPoiName();
    }

    public String resolveShareNote(SavedItinerary entity, ItineraryVO itinerary) {
        if (itinerary != null && StringUtils.hasText(itinerary.getShareNote())) {
            return itinerary.getShareNote().trim();
        }
        if (entity != null && StringUtils.hasText(entity.getShareNote())) {
            return entity.getShareNote().trim();
        }
        return null;
    }

    public List<String> resolveHighlights(ItineraryVO itinerary) {
        if (itinerary != null && itinerary.getOptions() != null && !itinerary.getOptions().isEmpty()) {
            ItineraryOptionVO selected = itinerary.getOptions().stream()
                    .filter(option -> Objects.equals(option.getOptionKey(), itinerary.getSelectedOptionKey()))
                    .findFirst()
                    .orElse(itinerary.getOptions().get(0));
            if (selected.getHighlights() != null && !selected.getHighlights().isEmpty()) {
                return selected.getHighlights().stream()
                        .filter(StringUtils::hasText)
                        .limit(4)
                        .toList();
            }
        }

        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null
                ? Collections.emptyList()
                : itinerary.getNodes();
        return nodes.stream()
                .map(ItineraryNodeVO::getPoiName)
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
    }

    public String resolveCoverImage(ItineraryVO itinerary) {
        if (StringUtils.hasText(itinerary == null ? null : itinerary.getCoverImageUrl())) {
            return normalizeCoverImageUrl(itinerary.getCoverImageUrl().trim());
        }
        String seed = buildCoverSeed(itinerary);
        if (!StringUtils.hasText(seed)) {
            return DEFAULT_COVER_IMAGE_URL;
        }
        int index = Math.floorMod(seed.hashCode(), PRESET_COVER_IMAGE_URLS.size());
        return PRESET_COVER_IMAGE_URLS.get(index);
    }

    private String normalizeCoverImageUrl(String coverImageUrl) {
        if (LEGACY_DEFAULT_COVER_IMAGE_URL.equals(coverImageUrl)) {
            return DEFAULT_COVER_IMAGE_URL;
        }
        if (coverImageUrl.startsWith(LEGACY_PRESET_PREFIX)
                && !coverImageUrl.startsWith(VERSIONED_PRESET_PREFIX)
                && coverImageUrl.matches("^/community-covers/cover-[a-z-]+\\.svg$")) {
            return coverImageUrl.replace(LEGACY_PRESET_PREFIX, VERSIONED_PRESET_PREFIX);
        }
        return coverImageUrl;
    }

    public String buildTitle(GenerateReqDTO req, ItineraryVO itinerary) {
        if (StringUtils.hasText(itinerary == null ? null : itinerary.getCustomTitle())) {
            return itinerary.getCustomTitle().trim();
        }
        List<String> themes = req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes();
        if (!themes.isEmpty()) {
            return String.join(" / ", themes) + "行程";
        }
        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null
                ? Collections.emptyList()
                : itinerary.getNodes();
        if (!nodes.isEmpty()) {
            return nodes.get(0).getPoiName() + "路线";
        }
        String tripDate = req == null ? null : req.getTripDate();
        return StringUtils.hasText(tripDate) ? tripDate + "行程" : "我的行程";
    }

    private boolean containsAny(String source, String... keys) {
        if (!StringUtils.hasText(source) || keys == null || keys.length == 0) {
            return false;
        }
        for (String key : keys) {
            if (StringUtils.hasText(key) && source.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String buildCoverSeed(ItineraryVO itinerary) {
        if (itinerary == null) {
            return null;
        }
        StringBuilder seed = new StringBuilder();
        if (StringUtils.hasText(itinerary.getCustomTitle())) {
            seed.append(itinerary.getCustomTitle().trim());
        }
        if (StringUtils.hasText(itinerary.getSelectedOptionKey())) {
            seed.append('|').append(itinerary.getSelectedOptionKey().trim());
        }
        List<ItineraryNodeVO> nodes = itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes();
        for (ItineraryNodeVO node : nodes) {
            if (node != null && StringUtils.hasText(node.getPoiName())) {
                seed.append('|').append(node.getPoiName().trim());
            }
        }
        return seed.toString();
    }
}
