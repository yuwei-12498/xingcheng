package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.domain.ai.ChatPoiSkillService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Order(35)
public class PoiRecommendationSkill extends AbstractGeoSkill {

    private static final int RESULT_LIMIT = 5;

    private final ChatPoiSkillService chatPoiSkillService;

    public PoiRecommendationSkill(ChatPoiSkillService chatPoiSkillService) {
        this.chatPoiSkillService = chatPoiSkillService;
    }

    @Override
    public String skillName() {
        return "poi_recommendation";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        if (req == null
                || req.getAction() != null
                || !StringUtils.hasText(req.getQuestion())) {
            return false;
        }
        String question = questionOf(req);
        return hasRecommendationIntent(question)
                && !hasTransportIntent(question)
                && !hasNearbyIntent(question)
                && !hasHotelIntent(question)
                && !hasExplicitSearchIntent(question)
                && !hasReplaceOrEditIntent(question);
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String city = cityOf(req);
        String question = questionOf(req);
        List<Poi> pois = chatPoiSkillService == null ? List.of() : chatPoiSkillService.loadRelevantPois(req);
        List<ChatSkillPayloadVO.ResultItem> items = toResultItems(req, pois);
        ChatSkillPayloadVO payload = buildPayload(
                skillName(),
                "poi_recommendation",
                city,
                resolveSceneKeyword(question),
                "推荐点位",
                RESULT_LIMIT,
                0,
                items,
                "local-db",
                buildFallbackMessage(question, items.isEmpty())
        );
        payload.getQuery().setKeyword(resolveSceneKeyword(question));
        return payload;
    }

    private boolean hasRecommendationIntent(String question) {
        return containsAny(question,
                "推荐", "适合", "去哪", "去哪里", "哪儿", "哪里", "玩什么", "逛什么", "有什么值得",
                "有什么好", "安排一下", "点位", "地方", "景点", "街区", "商场");
    }

    private boolean hasTransportIntent(String question) {
        return containsAny(question,
                "交通", "怎么去", "怎么走", "地铁", "公交", "打车", "网约车", "步行", "骑行",
                "路线", "路程", "距离", "多久", "多远", "换乘");
    }

    private boolean hasNearbyIntent(String question) {
        return containsAny(question, "附近", "周边", "旁边", "就近");
    }

    private boolean hasHotelIntent(String question) {
        return containsAny(question, "酒店", "住宿", "住哪");
    }

    private boolean hasExplicitSearchIntent(String question) {
        return containsAny(question, "搜索", "搜", "找")
                || question.startsWith("我想去")
                || question.startsWith("想去")
                || question.startsWith("去")
                || question.startsWith("看看")
                || question.startsWith("打卡");
    }

    private boolean hasReplaceOrEditIntent(String question) {
        return containsAny(question,
                "换成", "替换", "删除", "去掉", "移除", "减少", "增加", "加一个", "加入", "添加", "插入",
                "调整", "改成", "改为", "重新生成", "生成路线", "规划路线", "生成行程");
    }

    private List<ChatSkillPayloadVO.ResultItem> toResultItems(ChatReqDTO req, List<Poi> pois) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>();
        BigDecimal userLat = toBigDecimal(req == null || req.getContext() == null ? null : req.getContext().getUserLat());
        BigDecimal userLng = toBigDecimal(req == null || req.getContext() == null ? null : req.getContext().getUserLng());
        int limit = Math.min(pois.size(), RESULT_LIMIT);
        for (int i = 0; i < limit; i++) {
            Poi poi = pois.get(i);
            if (poi == null || !StringUtils.hasText(poi.getName())) {
                continue;
            }
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(poi.getName());
            item.setAddress(poi.getAddress());
            item.setCategory(poi.getCategory());
            item.setLatitude(poi.getLatitude());
            item.setLongitude(poi.getLongitude());
            item.setCityName(poi.getCityName());
            item.setSource(StringUtils.hasText(poi.getSourceType()) ? poi.getSourceType() : "local-db");
            item.setDistanceMeters(estimateDistanceMeters(userLat, userLng, poi.getLatitude(), poi.getLongitude()));
            items.add(item);
        }
        return items;
    }

    private String buildFallbackMessage(String question, boolean empty) {
        if (empty) {
            return "我先没筛到特别合适的点位，你可以再告诉我更偏夜景、美食、拍照还是安静路线。";
        }
        if (containsAny(question, "晚上", "夜景", "夜游")) {
            return "我先挑了几个更适合晚上逛的点位，你先看卡片，再告诉我想走热闹还是安静路线。";
        }
        if (containsAny(question, "美食", "吃", "夜宵")) {
            return "我先挑了几个更适合边吃边逛的点位，你先看卡片，再告诉我更想要街区感还是商场感。";
        }
        if (containsAny(question, "雨", "下雨", "室内")) {
            return "我先挑了几个更适合雨天或室内逛的点位，你先看卡片，再告诉我更想拍照还是轻松逛。";
        }
        return "我先挑了几个更贴合你当前偏好的点位，你先看卡片，再告诉我更想走热闹、安静还是拍照路线。";
    }

    private String resolveSceneKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return "推荐点位";
        }
        if (containsAny(question, "晚上", "夜景", "夜游")) {
            return "晚上";
        }
        if (containsAny(question, "美食", "吃", "夜宵")) {
            return "美食";
        }
        if (containsAny(question, "雨", "下雨", "室内")) {
            return "雨天";
        }
        if (containsAny(question, "拍照", "出片")) {
            return "拍照";
        }
        return "推荐点位";
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }

    private Double estimateDistanceMeters(BigDecimal userLat,
                                          BigDecimal userLng,
                                          BigDecimal poiLat,
                                          BigDecimal poiLng) {
        if (userLat == null || userLng == null || poiLat == null || poiLng == null) {
            return null;
        }
        double r = 6371_000D;
        double a1 = Math.toRadians(userLat.doubleValue());
        double a2 = Math.toRadians(poiLat.doubleValue());
        double dLat = a2 - a1;
        double dLng = Math.toRadians(poiLng.doubleValue() - userLng.doubleValue());
        double hav = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(a1) * Math.cos(a2) * Math.pow(Math.sin(dLng / 2), 2);
        double distance = 2 * r * Math.asin(Math.sqrt(hav));
        return BigDecimal.valueOf(distance).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
