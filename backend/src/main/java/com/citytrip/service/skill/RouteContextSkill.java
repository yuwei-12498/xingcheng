package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.geo.GeoPoint;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Order(10)
public class RouteContextSkill extends AbstractGeoSkill {

    @Override
    public String skillName() {
        return "route_context";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        return req != null
                && req.getContext() != null
                && req.getContext().getItinerary() != null
                && req.getContext().getItinerary().getNodes() != null
                && !req.getContext().getItinerary().getNodes().isEmpty()
                && containsAny(questionOf(req), "这条路线", "当前行程", "这趟安排");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String city = cityOf(req);
        GeoPoint userPoint = resolveUserPoint(req);
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>();
        for (ChatReqDTO.ChatRouteNode node : req.getContext().getItinerary().getNodes()) {
            if (node == null || !StringUtils.hasText(node.getPoiName())) {
                continue;
            }
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(node.getPoiName().trim());
            item.setCategory(node.getCategory());
            item.setCityName(city);
            item.setSource("itinerary-route");
            item.setLatitude(node.getLatitude());
            item.setLongitude(node.getLongitude());
            item.setDistanceMeters(estimateDistanceMeters(userPoint, node.getLatitude(), node.getLongitude()));
            items.add(item);
        }
        String summary = req.getContext().getItinerary().getSummary();
        ChatSkillPayloadVO payload = buildPayload(skillName(), "route_context", city,
                summary, "route", items.size(), 0, items,
                "itinerary-route", "我先把当前行程里的站点列给你。");
        payload.getQuery().setKeyword(summary);
        return payload;
    }

    private GeoPoint resolveUserPoint(ChatReqDTO req) {
        if (req == null || req.getContext() == null) {
            return null;
        }
        Double lat = req.getContext().getUserLat();
        Double lng = req.getContext().getUserLng();
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)) {
            return null;
        }
        GeoPoint point = new GeoPoint(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
        return point.valid() ? point : null;
    }

    private Double estimateDistanceMeters(GeoPoint userPoint, BigDecimal latitude, BigDecimal longitude) {
        if (userPoint == null || !userPoint.valid() || latitude == null || longitude == null) {
            return null;
        }
        double r = 6371_000D;
        double a1 = Math.toRadians(userPoint.latitude().doubleValue());
        double a2 = Math.toRadians(latitude.doubleValue());
        double dLat = a2 - a1;
        double dLng = Math.toRadians(longitude.doubleValue() - userPoint.longitude().doubleValue());
        double hav = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(a1) * Math.cos(a2) * Math.pow(Math.sin(dLng / 2), 2);
        double distance = 2 * r * Math.asin(Math.sqrt(hav));
        return BigDecimal.valueOf(distance).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
