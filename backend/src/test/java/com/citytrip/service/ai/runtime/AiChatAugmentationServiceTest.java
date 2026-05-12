package com.citytrip.service.ai.runtime;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.ai.mcp.McpCapability;
import com.citytrip.service.ai.mcp.McpCapabilityRegistry;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.RetrievalDocument;
import com.citytrip.service.domain.ai.ChatRouteContextSkillService;
import com.citytrip.service.impl.vivo.VivoFunctionCallingService;
import com.citytrip.service.impl.vivo.VivoToolDefinition;
import com.citytrip.service.impl.vivo.VivoToolRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatAugmentationServiceTest {

    @Test
    void shouldPassCurrentUserIdIntoRagExecutionContext() {
        AiRetrieverFacade retrieverFacade = new AiRetrieverFacade(List.of(context -> {
            assertThat(context.getUserId()).isEqualTo(77L);
            return List.of();
        }));

        AiChatAugmentationService service = new AiChatAugmentationService(
                retrieverFacade,
                null,
                null
        );

        ChatReqDTO.ChatContext chatContext = new ChatReqDTO.ChatContext();
        chatContext.setCurrentUserId(77L);
        chatContext.setCityName("成都");

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("按我喜欢的路线继续推荐");
        req.setContext(chatContext);

        AiChatAugmentationContext context = service.build(
                req,
                ChatRouteContextSkillService.RouteContext.empty(),
                false,
                false
        );

        assertThat(context.ragDocuments()).isEmpty();
    }

    @Test
    void shouldCollectRagToolContextAndAvoidDuplicateGeoSearchMcpForNearbyQuestion() {
        AiRetrieverFacade retrieverFacade = new AiRetrieverFacade(List.of(context -> {
            assertThat(context.getScene()).isEqualTo(AiScene.CHAT_QA);
            assertThat(context.getUserInput()).isEqualTo("万象城附近有什么推荐？");
            assertThat(context.getCityName()).isEqualTo("成都");
            assertThat(context.isRagEnabled()).isTrue();
            assertThat(context.isToolEnabled()).isTrue();
            assertThat(context.isMcpEnabled()).isTrue();
            return List.of(new RetrievalDocument("city-guide", "成都万象城适合逛吃和雨天室内路线。"));
        }));

        VivoToolRegistry toolRegistry = new VivoToolRegistry();
        toolRegistry.register(new VivoToolDefinition(
                "search_poi",
                "search poi",
                "{}",
                arguments -> "{\"tool\":\"search_poi\",\"status\":\"ok\",\"results\":[{\"name\":\"成都万象城\",\"distanceMeters\":320}]}"
        ));
        VivoFunctionCallingService functionCallingService = new VivoFunctionCallingService(toolRegistry);

        McpCapabilityRegistry mcpRegistry = new McpCapabilityRegistry(List.of(new McpCapability() {
            @Override
            public String capabilityName() {
                return "geo.search";
            }

            @Override
            public Object execute(Object input) {
                return Map.of(
                        "capability", "geo.search",
                        "status", "ok",
                        "results", List.of(Map.of("name", "成都万象城", "distanceMeters", 320))
                );
            }
        }));

        AiChatAugmentationService service = new AiChatAugmentationService(
                retrieverFacade,
                functionCallingService,
                mcpRegistry
        );

        AiChatAugmentationContext context = service.build(
                chatRequest("万象城附近有什么推荐？", "成都"),
                ChatRouteContextSkillService.RouteContext.empty(),
                true,
                true
        );

        assertThat(context.ragDocuments()).contains("city-guide: 成都万象城适合逛吃和雨天室内路线。");
        assertThat(context.toolPayloads()).anySatisfy(payload ->
                assertThat(payload).contains("来源=search_poi").contains("成都万象城"));
        assertThat(context.mcpEvidence()).isEmpty();
        assertThat(context.evidence()).contains("rag:city-guide", "tool:search_poi");
        assertThat(context.evidence()).doesNotContain("mcp:geo.search");
    }

    @Test
    void shouldSummarizeCommunityToolPayloadWithTitleAndRouteSummary() {
        VivoToolRegistry toolRegistry = new VivoToolRegistry();
        toolRegistry.register(new VivoToolDefinition(
                "search_community_posts",
                "search community posts",
                "{}",
                arguments -> "{\"tool\":\"search_community_posts\",\"status\":\"ok\",\"results\":[{\"title\":\"成都拍照路线\",\"shareNote\":\"下午到夜景更顺\",\"routeSummary\":\"春熙路 -> IFS -> 太古里\",\"themes\":[\"拍照\",\"citywalk\"]}]}"
        ));
        VivoFunctionCallingService functionCallingService = new VivoFunctionCallingService(toolRegistry);

        AiChatAugmentationService service = new AiChatAugmentationService(
                null,
                functionCallingService,
                null
        );

        AiChatAugmentationContext context = service.build(
                chatRequest("有没有成都拍照攻略帖子？", "成都"),
                ChatRouteContextSkillService.RouteContext.empty(),
                true,
                false
        );

        assertThat(context.toolPayloads()).singleElement().satisfies(payload ->
                assertThat(payload)
                        .contains("成都拍照路线")
                        .contains("春熙路 -> IFS -> 太古里")
                        .contains("拍照")
        );
    }

    @Test
    void shouldCollectRouteAmapEvidenceForRouteTimingQuestion() {
        McpCapabilityRegistry mcpRegistry = new McpCapabilityRegistry(List.of(
                new McpCapability() {
                    @Override
                    public String capabilityName() {
                        return "route.context";
                    }

                    @Override
                    public Object execute(Object input) {
                        return Map.of(
                                "capability", "route.context",
                                "status", "ok",
                                "summary", "商业区拍照路线",
                                "nodes", List.of(Map.of("name", "春熙路"), Map.of("name", "万象城"))
                        );
                    }
                },
                new McpCapability() {
                    @Override
                    public String capabilityName() {
                        return "route.amap";
                    }

                    @Override
                    public Object execute(Object input) {
                        return Map.of(
                                "capability", "route.amap",
                                "status", "ok",
                                "from", "春熙路",
                                "to", "万象城",
                                "estimatedMinutes", 18,
                                "estimatedDistanceKm", 5.2,
                                "transportMode", "地铁+步行"
                        );
                    }
                }
        ));

        AiChatAugmentationService service = new AiChatAugmentationService(
                null,
                null,
                mcpRegistry
        );

        ChatReqDTO req = routeQuestionRequest();
        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "option-1",
                "商业区拍照路线",
                req.getContext().getItinerary().getNodes()
        );

        AiChatAugmentationContext context = service.build(req, routeContext, false, true);

        assertThat(context.mcpEvidence()).anySatisfy(payload ->
                assertThat(payload)
                        .contains("来源=route.amap")
                        .contains("18")
                        .contains("5.2")
                        .contains("地铁+步行")
        );
        assertThat(context.evidence()).contains("mcp:route.amap");
    }

    @Test
    void shouldCollectGeoNearbyEvidenceForNearbyIntentWhenToolPayloadMissing() {
        McpCapabilityRegistry mcpRegistry = new McpCapabilityRegistry(List.of(new McpCapability() {
            @Override
            public String capabilityName() {
                return "geo.nearby";
            }

            @Override
            public Object execute(Object input) {
                return Map.of(
                        "capability", "geo.nearby",
                        "status", "ok",
                        "results", List.of(Map.of("name", "成都博物馆", "distanceMeters", 280, "category", "景点"))
                );
            }
        }));

        AiChatAugmentationService service = new AiChatAugmentationService(
                null,
                null,
                mcpRegistry
        );

        ChatReqDTO.ChatContext chatContext = new ChatReqDTO.ChatContext();
        chatContext.setCityName("成都");
        chatContext.setUserLat(30.657D);
        chatContext.setUserLng(104.066D);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("我附近有什么景点推荐？");
        req.setContext(chatContext);

        AiChatAugmentationContext context = service.build(
                req,
                ChatRouteContextSkillService.RouteContext.empty(),
                false,
                true
        );

        assertThat(context.mcpEvidence()).anySatisfy(payload ->
                assertThat(payload).contains("来源=geo.nearby").contains("成都博物馆"));
        assertThat(context.evidence()).contains("mcp:geo.nearby");
    }

    @Test
    void shouldCollectCommunitySearchEvidenceForCommunityIntent() {
        McpCapabilityRegistry mcpRegistry = new McpCapabilityRegistry(List.of(new McpCapability() {
            @Override
            public String capabilityName() {
                return "community.search";
            }

            @Override
            public Object execute(Object input) {
                return Map.of(
                        "capability", "community.search",
                        "status", "ok",
                        "results", List.of(Map.of(
                                "title", "成都拍照夜景路线",
                                "routeSummary", "春熙路 -> IFS -> 太古里",
                                "themes", List.of("拍照", "citywalk")
                        ))
                );
            }
        }));

        AiChatAugmentationService service = new AiChatAugmentationService(
                null,
                null,
                mcpRegistry
        );

        AiChatAugmentationContext context = service.build(
                chatRequest("有没有成都拍照攻略帖子？", "成都"),
                ChatRouteContextSkillService.RouteContext.empty(),
                false,
                true
        );

        assertThat(context.mcpEvidence()).anySatisfy(payload ->
                assertThat(payload).contains("来源=community.search").contains("成都拍照夜景路线"));
        assertThat(context.evidence()).contains("mcp:community.search");
    }

    @Test
    void shouldCollectItinerarySnapshotEvidenceForRouteVerifyIntent() {
        McpCapabilityRegistry mcpRegistry = new McpCapabilityRegistry(List.of(
                new McpCapability() {
                    @Override
                    public String capabilityName() {
                        return "route.context";
                    }

                    @Override
                    public Object execute(Object input) {
                        return Map.of(
                                "capability", "route.context",
                                "status", "ok",
                                "summary", "商业区拍照路线",
                                "nodes", List.of(Map.of("name", "春熙路"), Map.of("name", "IFS"))
                        );
                    }
                },
                new McpCapability() {
                    @Override
                    public String capabilityName() {
                        return "route.amap";
                    }

                    @Override
                    public Object execute(Object input) {
                        return Map.of(
                                "capability", "route.amap",
                                "status", "ok",
                                "from", "春熙路",
                                "to", "IFS",
                                "estimatedMinutes", 12,
                                "transportMode", "步行"
                        );
                    }
                },
                new McpCapability() {
                    @Override
                    public String capabilityName() {
                        return "itinerary.snapshot";
                    }

                    @Override
                    public Object execute(Object input) {
                        return Map.of(
                                "capability", "itinerary.snapshot",
                                "status", "ok",
                                "selectedOptionKey", "option-1",
                                "summary", "商业区拍照路线",
                                "routePath", "春熙路 -> IFS"
                        );
                    }
                }
        ));

        AiChatAugmentationService service = new AiChatAugmentationService(
                null,
                null,
                mcpRegistry
        );

        ChatReqDTO req = routeQuestionRequest();
        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "option-1",
                "商业区拍照路线",
                req.getContext().getItinerary().getNodes()
        );

        AiChatAugmentationContext context = service.build(req, routeContext, false, true);

        assertThat(context.mcpEvidence()).anySatisfy(payload ->
                assertThat(payload).contains("来源=itinerary.snapshot").contains("春熙路 -> IFS"));
        assertThat(context.evidence()).contains("mcp:itinerary.snapshot", "mcp:route.amap");
    }

    @Test
    void shouldNotInjectUnavailableMcpPayloadIntoModelContext() {
        McpCapabilityRegistry mcpRegistry = new McpCapabilityRegistry(List.of(new McpCapability() {
            @Override
            public String capabilityName() {
                return "geo.search";
            }

            @Override
            public Object execute(Object input) {
                return Map.of(
                        "capability", "geo.search",
                        "status", "unavailable",
                        "message", "geo service is not configured"
                );
            }
        }));
        AiChatAugmentationService service = new AiChatAugmentationService(
                null,
                null,
                mcpRegistry
        );

        AiChatAugmentationContext context = service.build(
                chatRequest("太古里附近有什么推荐？", "成都"),
                ChatRouteContextSkillService.RouteContext.empty(),
                false,
                true
        );

        assertThat(context.mcpEvidence()).isEmpty();
        assertThat(context.evidence()).contains("mcp:geo.search:unavailable");
    }

    private ChatReqDTO chatRequest(String question, String cityName) {
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName(cityName);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);
        req.setContext(context);
        return req;
    }

    private ChatReqDTO routeQuestionRequest() {
        ChatReqDTO.ChatRouteNode from = new ChatReqDTO.ChatRouteNode();
        from.setPoiName("春熙路");
        from.setLatitude(BigDecimal.valueOf(30.657));
        from.setLongitude(BigDecimal.valueOf(104.080));

        ChatReqDTO.ChatRouteNode to = new ChatReqDTO.ChatRouteNode();
        to.setPoiName("万象城");
        to.setLatitude(BigDecimal.valueOf(30.659));
        to.setLongitude(BigDecimal.valueOf(104.114));

        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setItineraryId(88L);
        itinerary.setSelectedOptionKey("option-1");
        itinerary.setSummary("商业区拍照路线");
        itinerary.setNodes(List.of(from, to));

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        context.setItinerary(itinerary);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("春熙路到万象城多久？");
        req.setContext(context);
        return req;
    }
}
