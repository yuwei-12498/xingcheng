package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteContextSkillTest {

    @Test
    void execute_shouldComputeRouteNodeDistanceWhenUserLocationExists() {
        RouteContextSkill skill = new RouteContextSkill();
        ChatReqDTO req = requestWithLocation(30.660000D, 104.070000D);

        ChatSkillPayloadVO payload = skill.execute(req);

        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getDistanceMeters()).isNotNull();
        assertThat(payload.getResults().get(0).getDistanceMeters()).isGreaterThan(100D);
    }

    @Test
    void execute_shouldLeaveDistanceEmptyWhenUserLocationIsMissing() {
        RouteContextSkill skill = new RouteContextSkill();
        ChatReqDTO req = requestWithLocation(null, null);

        ChatSkillPayloadVO payload = skill.execute(req);

        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getDistanceMeters()).isNull();
    }

    private ChatReqDTO requestWithLocation(Double lat, Double lng) {
        ChatReqDTO.ChatRouteNode node = new ChatReqDTO.ChatRouteNode();
        node.setPoiName("太古里");
        node.setCategory("商业购物");
        node.setLatitude(new BigDecimal("30.653000"));
        node.setLongitude(new BigDecimal("104.082000"));

        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setSummary("当前行程");
        itinerary.setNodes(List.of(node));

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        context.setUserLat(lat);
        context.setUserLng(lng);
        context.setItinerary(itinerary);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("当前行程有哪些站点");
        req.setContext(context);
        return req;
    }
}
