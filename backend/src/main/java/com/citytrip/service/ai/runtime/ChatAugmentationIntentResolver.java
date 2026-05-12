package com.citytrip.service.ai.runtime;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.domain.ai.ChatRouteContextSkillService;
import org.springframework.util.StringUtils;

import java.util.List;

public class ChatAugmentationIntentResolver {
    private static final List<String> NEARBY_MARKERS = List.of("附近", "周边", "旁边");
    private static final List<String> COMMUNITY_MARKERS = List.of("社区", "帖子", "攻略");
    private static final List<String> ROUTE_MARKERS = List.of("多久", "多远", "怎么走", "路线", "交通", "耗时", "赶", "顺路");

    public ChatAugmentationIntent resolve(ChatReqDTO req,
                                          ChatRouteContextSkillService.RouteContext routeContext) {
        String question = req == null ? "" : safe(req.getQuestion());
        if (containsAny(question, NEARBY_MARKERS)) {
            return ChatAugmentationIntent.NEARBY_DISCOVERY;
        }
        if (containsAny(question, COMMUNITY_MARKERS)) {
            return ChatAugmentationIntent.COMMUNITY_GUIDE;
        }
        boolean hasRouteContext = routeContext != null && routeContext.available();
        boolean hasItinerary = req != null
                && req.getContext() != null
                && req.getContext().getItinerary() != null
                && req.getContext().getItinerary().getNodes() != null
                && !req.getContext().getItinerary().getNodes().isEmpty();
        if ((hasRouteContext || hasItinerary) && containsAny(question, ROUTE_MARKERS)) {
            return ChatAugmentationIntent.ROUTE_VERIFY;
        }
        return ChatAugmentationIntent.GENERAL_QA;
    }

    private boolean containsAny(String value, List<String> markers) {
        if (!StringUtils.hasText(value) || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}