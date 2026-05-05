package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRouteContextSkillServiceTest {

    private final ChatRouteContextSkillService service = new ChatRouteContextSkillService();

    @Test
    void shouldResolveCurrentRouteContextWithSelectedOptionAndFirstNode() {
        ChatReqDTO req = new ChatReqDTO();
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setSelectedOptionKey("B");
        itinerary.setSummary("city culture line");

        ChatReqDTO.ChatRouteNode firstNode = new ChatReqDTO.ChatRouteNode();
        firstNode.setPoiName("Kuanzhai Alley");
        firstNode.setTravelTime(18);
        firstNode.setTravelTransportMode("metro");
        firstNode.setTravelDistanceKm(BigDecimal.valueOf(4.6));

        ChatReqDTO.ChatRouteNode secondNode = new ChatReqDTO.ChatRouteNode();
        secondNode.setPoiName("Wuhou Shrine");
        secondNode.setTravelTime(21);

        itinerary.setNodes(List.of(firstNode, secondNode));
        context.setItinerary(itinerary);
        req.setContext(context);

        ChatRouteContextSkillService.RouteContext routeContext = service.resolve(req);

        assertThat(routeContext.available()).isTrue();
        assertThat(routeContext.selectedOptionKey()).isEqualTo("B");
        assertThat(routeContext.summary()).isEqualTo("city culture line");
        assertThat(routeContext.nodes()).hasSize(2);
        assertThat(routeContext.firstNode().getPoiName()).isEqualTo("Kuanzhai Alley");
    }

    @Test
    void shouldIgnoreInvalidNodesAndLimitMaxRouteNodeCount() {
        ChatReqDTO req = new ChatReqDTO();
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();

        List<ChatReqDTO.ChatRouteNode> nodes = new ArrayList<>();
        ChatReqDTO.ChatRouteNode invalid = new ChatReqDTO.ChatRouteNode();
        invalid.setPoiName(" ");
        nodes.add(invalid);

        for (int i = 0; i < 20; i++) {
            ChatReqDTO.ChatRouteNode node = new ChatReqDTO.ChatRouteNode();
            node.setPoiName("POI-" + i);
            nodes.add(node);
        }
        itinerary.setNodes(nodes);
        context.setItinerary(itinerary);
        req.setContext(context);

        ChatRouteContextSkillService.RouteContext routeContext = service.resolve(req);

        assertThat(routeContext.available()).isTrue();
        assertThat(routeContext.nodes()).hasSize(16);
        assertThat(routeContext.firstNode().getPoiName()).isEqualTo("POI-0");
    }
}