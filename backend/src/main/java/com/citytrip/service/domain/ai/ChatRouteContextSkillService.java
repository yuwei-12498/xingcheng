package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatRouteContextSkillService {

    private static final int MAX_ROUTE_NODE_COUNT = 16;

    public RouteContext resolve(ChatReqDTO req) {
        if (req == null
                || req.getContext() == null
                || req.getContext().getItinerary() == null
                || req.getContext().getItinerary().getNodes() == null
                || req.getContext().getItinerary().getNodes().isEmpty()) {
            return RouteContext.empty();
        }
        List<ChatReqDTO.ChatRouteNode> sanitizedNodes = new ArrayList<>();
        for (ChatReqDTO.ChatRouteNode node : req.getContext().getItinerary().getNodes()) {
            if (node == null || !StringUtils.hasText(node.getPoiName())) {
                continue;
            }
            sanitizedNodes.add(node);
            if (sanitizedNodes.size() >= MAX_ROUTE_NODE_COUNT) {
                break;
            }
        }
        if (sanitizedNodes.isEmpty()) {
            return RouteContext.empty();
        }
        return new RouteContext(
                req.getContext().getItinerary().getSelectedOptionKey(),
                req.getContext().getItinerary().getSummary(),
                Collections.unmodifiableList(sanitizedNodes)
        );
    }

    public record RouteContext(String selectedOptionKey,
                               String summary,
                               List<ChatReqDTO.ChatRouteNode> nodes) {
        public static RouteContext empty() {
            return new RouteContext(null, null, Collections.emptyList());
        }

        public boolean available() {
            return nodes != null && !nodes.isEmpty();
        }

        public ChatReqDTO.ChatRouteNode firstNode() {
            if (!available()) {
                return null;
            }
            return nodes.get(0);
        }
    }
}

