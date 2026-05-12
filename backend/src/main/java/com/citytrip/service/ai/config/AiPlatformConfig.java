package com.citytrip.service.ai.config;

import com.citytrip.config.LlmProperties;
import com.citytrip.service.ChatService;
import com.citytrip.service.LlmService;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.ai.adapter.LangChainChatServiceAdapter;
import com.citytrip.service.ai.adapter.LangChainLlmServiceAdapter;
import com.citytrip.service.ai.mcp.AmapRouteMcpCapability;
import com.citytrip.service.ai.mcp.CommunitySearchMcpCapability;
import com.citytrip.service.ai.mcp.GeoNearbyMcpCapability;
import com.citytrip.service.ai.mcp.GeoSearchMcpCapability;
import com.citytrip.service.ai.mcp.ItinerarySnapshotMcpCapability;
import com.citytrip.service.ai.mcp.McpCapability;
import com.citytrip.service.ai.mcp.McpCapabilityRegistry;
import com.citytrip.service.ai.mcp.RouteContextMcpCapability;
import com.citytrip.service.ai.orchestrator.AiExecutionContextFactory;
import com.citytrip.service.ai.orchestrator.AiSceneRouter;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.CityGuideRetriever;
import com.citytrip.service.ai.rag.CommunityPostRetriever;
import com.citytrip.service.ai.rag.ContextRetriever;
import com.citytrip.service.ai.rag.PoiAliasResolver;
import com.citytrip.service.ai.rag.PoiFactRepository;
import com.citytrip.service.ai.rag.PoiKnowledgeRetriever;
import com.citytrip.service.ai.rag.QueryRewriteService;
import com.citytrip.service.ai.rag.RetrievalRankingService;
import com.citytrip.service.ai.rag.RouteHistoryRetriever;
import com.citytrip.service.ai.runtime.AiChatAugmentationService;
import com.citytrip.service.ai.tool.PoiSearchTool;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.impl.AmapTravelTimeServiceImpl;
import com.citytrip.service.impl.RealChatGatewayService;
import com.citytrip.service.impl.RealLlmGatewayService;
import com.citytrip.service.impl.vivo.VivoFunctionCallingService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiPlatformConfig {

    @Bean
    public AiSceneRouter aiSceneRouter() {
        return new AiSceneRouter();
    }

    @Bean
    public AiExecutionContextFactory aiExecutionContextFactory() {
        return new AiExecutionContextFactory();
    }

    @Bean
    public LangChainAiOrchestrator langChainAiOrchestrator(AiSceneRouter aiSceneRouter) {
        return new LangChainAiOrchestrator(aiSceneRouter);
    }

    @Bean
    public LangChainLlmServiceAdapter langChainLlmServiceAdapter(LangChainAiOrchestrator orchestrator,
                                                                 AiRetrieverFacade aiRetrieverFacade,
                                                                 RealLlmGatewayService realLlmService) {
        return new LangChainLlmServiceAdapter(orchestrator, aiRetrieverFacade, realLlmService);
    }

    @Bean
    public PoiAliasResolver poiAliasResolver() {
        return new PoiAliasResolver();
    }

    @Bean
    public QueryRewriteService queryRewriteService(PoiAliasResolver poiAliasResolver) {
        return new QueryRewriteService(poiAliasResolver);
    }

    @Bean
    public CityGuideRetriever cityGuideRetriever() {
        return new CityGuideRetriever();
    }

    @Bean
    public PoiFactRepository poiFactRepository(ObjectProvider<ObjectMapper> objectMapper) {
        return new PoiFactRepository(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    public PoiKnowledgeRetriever poiKnowledgeRetriever(PoiFactRepository poiFactRepository) {
        return new PoiKnowledgeRetriever(poiFactRepository);
    }

    @Bean
    public CommunityPostRetriever communityPostRetriever(ObjectProvider<CommunityItineraryQueryService> communityItineraryQueryService) {
        return new CommunityPostRetriever(communityItineraryQueryService.getIfAvailable());
    }

    @Bean
    public RouteHistoryRetriever routeHistoryRetriever(ObjectProvider<SavedItineraryRepository> savedItineraryRepository,
                                                       ObjectProvider<SavedItineraryCodec> savedItineraryCodec) {
        return new RouteHistoryRetriever(
                savedItineraryRepository.getIfAvailable(),
                savedItineraryCodec.getIfAvailable()
        );
    }

    @Bean
    public AiRetrieverFacade aiRetrieverFacade(List<ContextRetriever> retrievers,
                                               ObjectProvider<LlmProperties> llmProperties) {
        LlmProperties properties = llmProperties.getIfAvailable();
        return new AiRetrieverFacade(
                retrievers,
                new RetrievalRankingService(properties == null ? null : properties.getAiPlatform().getRetrieval())
        );
    }

    @Bean
    public GeoSearchMcpCapability geoSearchMcpCapability(ObjectProvider<GeoSearchService> geoSearchService) {
        return new GeoSearchMcpCapability(geoSearchService.getIfAvailable());
    }

    @Bean
    public GeoNearbyMcpCapability geoNearbyMcpCapability(ObjectProvider<GeoSearchService> geoSearchService) {
        return new GeoNearbyMcpCapability(geoSearchService.getIfAvailable());
    }

    @Bean
    public AmapRouteMcpCapability amapRouteMcpCapability(ObjectProvider<AmapTravelTimeServiceImpl> amapTravelTimeService,
                                                         ObjectProvider<TravelTimeService> travelTimeService) {
        TravelTimeService service = amapTravelTimeService.getIfAvailable();
        if (service == null) {
            service = travelTimeService.getIfAvailable();
        }
        return new AmapRouteMcpCapability(service);
    }

    @Bean
    public RouteContextMcpCapability routeContextMcpCapability() {
        return new RouteContextMcpCapability();
    }

    @Bean
    public CommunitySearchMcpCapability communitySearchMcpCapability(ObjectProvider<CommunityItineraryQueryService> communityItineraryQueryService) {
        return new CommunitySearchMcpCapability(communityItineraryQueryService.getIfAvailable());
    }

    @Bean
    public ItinerarySnapshotMcpCapability itinerarySnapshotMcpCapability(ObjectProvider<SavedItineraryRepository> savedItineraryRepository,
                                                                         ObjectProvider<SavedItineraryCodec> savedItineraryCodec) {
        return new ItinerarySnapshotMcpCapability(
                savedItineraryRepository.getIfAvailable(),
                savedItineraryCodec.getIfAvailable()
        );
    }

    @Bean
    public McpCapabilityRegistry mcpCapabilityRegistry(List<McpCapability> capabilities) {
        return new McpCapabilityRegistry(capabilities);
    }

    @Bean
    public PoiSearchTool poiSearchTool(McpCapabilityRegistry mcpCapabilityRegistry) {
        return new PoiSearchTool(mcpCapabilityRegistry);
    }

    @Bean
    public AiChatAugmentationService aiChatAugmentationService(AiRetrieverFacade aiRetrieverFacade,
                                                               ObjectProvider<VivoFunctionCallingService> vivoFunctionCallingService,
                                                               ObjectProvider<McpCapabilityRegistry> mcpCapabilityRegistry,
                                                               ObjectProvider<ObjectMapper> objectMapper,
                                                               ObjectProvider<LlmProperties> llmProperties) {
        LlmProperties properties = llmProperties.getIfAvailable();
        return new AiChatAugmentationService(
                aiRetrieverFacade,
                vivoFunctionCallingService.getIfAvailable(),
                mcpCapabilityRegistry.getIfAvailable(),
                objectMapper.getIfAvailable(ObjectMapper::new),
                properties == null ? null : properties.getAiPlatform().getAugmentation()
        );
    }

    @Bean
    public LangChainChatServiceAdapter langChainChatServiceAdapter(LangChainAiOrchestrator orchestrator,
                                                                   AiRetrieverFacade aiRetrieverFacade,
                                                                   QueryRewriteService queryRewriteService,
                                                                   RealChatGatewayService realChatService) {
        return new LangChainChatServiceAdapter(orchestrator, aiRetrieverFacade, queryRewriteService, realChatService);
    }

    @Bean(name = "aiOrchestratorLlmService")
    public LlmService aiOrchestratorLlmService(LangChainLlmServiceAdapter adapter) {
        return adapter;
    }

    @Bean(name = "aiOrchestratorChatService")
    public ChatService aiOrchestratorChatService(LangChainChatServiceAdapter adapter) {
        return adapter;
    }
}
