package com.citytrip.service.ai.runtime;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.domain.ai.ChatRouteContextSkillService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAugmentationIntentResolverTest {

    private final ChatAugmentationIntentResolver resolver = new ChatAugmentationIntentResolver();

    @Test
    void shouldResolveNearbyDiscoveryIntent() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("万象城附近有什么推荐？");

        assertThat(resolver.resolve(req, ChatRouteContextSkillService.RouteContext.empty()))
                .isEqualTo(ChatAugmentationIntent.NEARBY_DISCOVERY);
    }

    @Test
    void shouldResolveCommunityGuideIntent() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("有没有成都拍照攻略帖子？");

        assertThat(resolver.resolve(req, ChatRouteContextSkillService.RouteContext.empty()))
                .isEqualTo(ChatAugmentationIntent.COMMUNITY_GUIDE);
    }

    @Test
    void shouldResolveRouteVerifyIntentWhenRouteContextAndTimingQuestionExist() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("春熙路到万象城多久？");
        ChatReqDTO.ChatRouteNode from = new ChatReqDTO.ChatRouteNode();
        from.setPoiName("春熙路");
        ChatReqDTO.ChatRouteNode to = new ChatReqDTO.ChatRouteNode();
        to.setPoiName("万象城");

        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "option-1",
                "商业区夜景路线",
                List.of(from, to)
        );

        assertThat(resolver.resolve(req, routeContext))
                .isEqualTo(ChatAugmentationIntent.ROUTE_VERIFY);
    }

    @Test
    void shouldResolveGeneralQaAsFallback() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("成都两天一夜怎么安排比较轻松？");

        assertThat(resolver.resolve(req, ChatRouteContextSkillService.RouteContext.empty()))
                .isEqualTo(ChatAugmentationIntent.GENERAL_QA);
    }
}