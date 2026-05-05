package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.domain.ai.ChatEvidenceSkillService;
import com.citytrip.service.domain.ai.ChatFactGuardService;
import com.citytrip.service.domain.ai.ChatFirstLegEtaSkillService;
import com.citytrip.service.domain.ai.ChatGeoSkillService;
import com.citytrip.service.domain.ai.ChatPoiSkillService;
import com.citytrip.service.domain.ai.ChatRouteContextSkillService;
import com.citytrip.service.domain.ai.ChatSegmentTransportSkillService;
import com.citytrip.service.impl.vivo.VivoFunctionCallingService;
import com.citytrip.service.skill.SkillRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class RealChatGatewayService {
    private static final Logger log = LoggerFactory.getLogger(RealChatGatewayService.class);
    private final OpenAiGatewayClient openAiGatewayClient;
    private final LlmProperties llmProperties;
    private final SafePromptBuilder safePromptBuilder;
    private final ChatPoiSkillService chatPoiSkillService;
    private final ChatGeoSkillService chatGeoSkillService;
    private final ChatFactGuardService chatFactGuardService;
    private final ChatRouteContextSkillService chatRouteContextSkillService;
    private final ChatFirstLegEtaSkillService chatFirstLegEtaSkillService;
    private final ChatSegmentTransportSkillService chatSegmentTransportSkillService;
    private final ChatEvidenceSkillService chatEvidenceSkillService;
    @Autowired(required = false)
    private SkillRouterService skillRouterService;
    @Autowired(required = false)
    private VivoFunctionCallingService vivoFunctionCallingService;
    @Autowired(required = false)
    private RealLlmGatewayService realLlmGatewayService;

    @Autowired
    public RealChatGatewayService(OpenAiGatewayClient openAiGatewayClient,
                                  LlmProperties llmProperties,
                                  SafePromptBuilder safePromptBuilder,
                                  ChatPoiSkillService chatPoiSkillService,
                                  ChatGeoSkillService chatGeoSkillService,
                                  ChatFactGuardService chatFactGuardService,
                                  ChatRouteContextSkillService chatRouteContextSkillService,
                                  ChatFirstLegEtaSkillService chatFirstLegEtaSkillService,
                                  ChatSegmentTransportSkillService chatSegmentTransportSkillService,
                                  ChatEvidenceSkillService chatEvidenceSkillService) {
        this.openAiGatewayClient = openAiGatewayClient;
        this.llmProperties = llmProperties;
        this.safePromptBuilder = safePromptBuilder;
        this.chatPoiSkillService = chatPoiSkillService;
        this.chatGeoSkillService = chatGeoSkillService;
        this.chatFactGuardService = chatFactGuardService;
        this.chatRouteContextSkillService = chatRouteContextSkillService;
        this.chatFirstLegEtaSkillService = chatFirstLegEtaSkillService;
        this.chatSegmentTransportSkillService = chatSegmentTransportSkillService;
        this.chatEvidenceSkillService = chatEvidenceSkillService;
    }

    public RealChatGatewayService(OpenAiGatewayClient openAiGatewayClient,
                                  LlmProperties llmProperties,
                                  SafePromptBuilder safePromptBuilder,
                                  ChatPoiSkillService chatPoiSkillService) {
        this(
                openAiGatewayClient,
                llmProperties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public ChatVO answerQuestion(ChatReqDTO req) {
        ChatGenerationResult result = callChatCompletion(req, null);
        ChatVO vo = new ChatVO();
        vo.setAnswer(result.answer());
        vo.setRelatedTips(buildRelatedTips(req));
        vo.setEvidence(result.evidence());
        vo.setSkillPayload(result.skillPayload());
        return vo;
    }

    public ChatVO streamAnswer(ChatReqDTO req, Consumer<String> tokenConsumer) {
        ChatGenerationResult result = callChatCompletion(req, tokenConsumer);
        ChatVO vo = new ChatVO();
        vo.setAnswer(result.answer());
        vo.setRelatedTips(buildRelatedTips(req));
        vo.setEvidence(result.evidence());
        vo.setSkillPayload(result.skillPayload());
        return vo;
    }

    private ChatGenerationResult callChatCompletion(ChatReqDTO req, Consumer<String> tokenConsumer) {
        if (llmProperties == null || !llmProperties.canTryRealChat()) {
            throw new IllegalStateException("OpenAI real chat model is not configured");
        }
        if (openAiGatewayClient == null) {
            throw new IllegalStateException("OpenAI gateway is not configured");
        }

        ChatRouteContextSkillService.RouteContext routeContext = chatRouteContextSkillService == null
                ? ChatRouteContextSkillService.RouteContext.empty()
                : chatRouteContextSkillService.resolve(req);
        LinkedHashSet<String> usedSkills = new LinkedHashSet<>();
        if (routeContext.available()) {
            usedSkills.add("RouteContextSkill");
        }

        ChatGenerationResult directSkillResult = tryDirectSkillAnswer(req, tokenConsumer, routeContext, usedSkills);
        if (directSkillResult != null) {
            return directSkillResult;
        }

        ChatGenerationResult skillRouterResult = trySkillRouterAnswer(req, tokenConsumer, routeContext, usedSkills);
        if (skillRouterResult != null) {
            return skillRouterResult;
        }

        LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
        String systemPrompt = safePromptBuilder.buildChatSystemPrompt();

        List<Poi> chatPois = chatPoiSkillService == null ? List.of() : chatPoiSkillService.loadRelevantPois(req);
        if (!chatPois.isEmpty()) {
            usedSkills.add("PoiSkill");
        }
        ChatGeoSkillService.GeoFactsResult geoFactsResult = chatGeoSkillService == null
                ? ChatGeoSkillService.GeoFactsResult.empty(false)
                : chatGeoSkillService.collectFacts(req, chatPois);
        if (geoFactsResult.geoIntent() || !geoFactsResult.facts().isEmpty()) {
            usedSkills.add("GeoSkill");
        }
        List<ChatGeoSkillService.GeoFact> routeFacts = collectRouteFacts(req);
        List<ChatGeoSkillService.GeoFact> guardFacts = mergeFactsForGuard(geoFactsResult.facts(), routeFacts);

        if (geoFactsResult.clarificationQuestion() != null && !geoFactsResult.clarificationQuestion().isBlank()) {
            String clarification = geoFactsResult.clarificationQuestion().trim();
            if (tokenConsumer != null) {
                tokenConsumer.accept(clarification);
            }
            ChatGenerationResult guarded = applyFactGuard(clarification, guardFacts);
            return new ChatGenerationResult(
                    guarded.answer(),
                    mergeEvidence(
                            guarded.evidence(),
                            routeContext,
                            usedSkills,
                            "geo-disambiguation",
                            buildRouteContextEvidence(routeContext)
                    ),
                    null
            );
        }

        String userPrompt = safePromptBuilder.buildChatUserPrompt(req, chatPois, geoFactsResult.facts());
        List<OpenAiGatewayClient.OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiGatewayClient.OpenAiMessage("system", systemPrompt));
        messages.add(new OpenAiGatewayClient.OpenAiMessage("user", userPrompt));
        String prefetchedToolPayload = maybePrefetchToolPayload(req);
        if (StringUtils.hasText(prefetchedToolPayload)) {
            usedSkills.add("FunctionCalling");
            messages.add(new OpenAiGatewayClient.OpenAiMessage("assistant", "Live tool result JSON: " + prefetchedToolPayload));
            messages.add(new OpenAiGatewayClient.OpenAiMessage(
                    "user",
                    "Please answer the original question using the live tool result above when relevant."
            ));
        }
        String answer = tokenConsumer == null
                ? openAiGatewayClient.request(chatOptions, llmProperties.getOpenai().getApiKey(), messages)
                : openAiGatewayClient.stream(chatOptions, llmProperties.getOpenai().getApiKey(), messages, tokenConsumer);
        if (answer == null || answer.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI returned empty chat answer");
        }
        if (llmProperties.getFeatures().isToolLoopEnabled()
                && vivoFunctionCallingService != null
                && vivoFunctionCallingService.shouldEnterToolLoop(answer)) {
            VivoFunctionCallingService.ToolLoopResult toolLoopResult = vivoFunctionCallingService.runToolLoop(
                    answer,
                    messages,
                    openAiGatewayClient,
                    chatOptions,
                    llmProperties.getOpenai().getApiKey()
            );
            answer = toolLoopResult.finalAnswer();
            usedSkills.add("FunctionCalling");
        }

        ChatGenerationResult guarded = applyFactGuard(answer.trim(), guardFacts);
        List<String> evidence = mergeEvidence(
                guarded.evidence(),
                routeContext,
                usedSkills,
                null,
                buildRouteContextEvidence(routeContext)
        );
        if (tokenConsumer != null
                && guarded.answer() != null
                && !guarded.answer().equals(answer.trim())) {
            String delta = guarded.answer().startsWith(answer.trim())
                    ? guarded.answer().substring(answer.trim().length())
                    : ("\n" + guarded.answer());
            if (!delta.isBlank()) {
                tokenConsumer.accept(delta);
            }
        }
        return new ChatGenerationResult(guarded.answer(), evidence, null);
    }

    private String maybePrefetchToolPayload(ChatReqDTO req) {
        if (!llmProperties.getFeatures().isPoiLiveEnabled()
                || vivoFunctionCallingService == null
                || req == null
                || !StringUtils.hasText(req.getQuestion())) {
            return null;
        }
        String question = req.getQuestion().trim();
        String city = req.getContext() == null ? "" : safe(req.getContext().getCityName());
        String poiKeyword = extractNearbyKeyword(question);
        if (StringUtils.hasText(poiKeyword)) {
            return vivoFunctionCallingService.executeToolCall(
                    "search_poi",
                    "{\"keyword\":\"" + escapeJson(poiKeyword) + "\",\"city\":\"" + escapeJson(city) + "\",\"limit\":5}"
            );
        }
        if (question.contains("社区") || question.contains("帖子") || question.contains("攻略")) {
            return vivoFunctionCallingService.executeToolCall(
                    "search_community_posts",
                    "{\"keyword\":\"" + escapeJson(question) + "\",\"limit\":5}"
            );
        }
        return null;
    }

    private String extractNearbyKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String normalized = question.trim();
        String[] markers = {"附近", "周边", "旁边"};
        for (String marker : markers) {
            int index = normalized.indexOf(marker);
            if (index > 0) {
                String keyword = normalized.substring(0, index)
                        .replace("请问", "")
                        .replace("想知道", "")
                        .replace("我想问", "")
                        .trim();
                if (StringUtils.hasText(keyword)) {
                    return keyword;
                }
            }
        }
        return null;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<String> buildRelatedTips(ChatReqDTO req) {
        String question = req == null ? "" : safe(req.getQuestion());
        String cityName = req == null || req.getContext() == null ? "" : safe(req.getContext().getCityName());
        if (realLlmGatewayService != null
                && llmProperties.getFeatures().isChatOnlineEnabled()
                && llmProperties.canTryRealText()) {
            try {
                List<String> onlineTips = sanitizeRelatedTips(
                        realLlmGatewayService.generateChatFollowUpTips(question, cityName)
                );
                if (!onlineTips.isEmpty()) {
                    return onlineTips;
                }
                log.warn("Online related tips returned empty result, fallback to local template tips.");
            } catch (Exception ex) {
                log.warn("Failed to generate online related tips, fallback to local template tips. reason={}", ex.getMessage());
            }
        }
        return buildFallbackRelatedTips(question);
    }

    private List<String> sanitizeRelatedTips(List<String> tips) {
        if (tips == null || tips.isEmpty()) {
            return List.of();
        }
        return tips.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(3)
                .toList();
    }

    private List<String> buildFallbackRelatedTips(String question) {
        List<String> tips = new ArrayList<>();
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (question.contains("拍照") || question.contains("机位")
                || normalized.contains("photo") || normalized.contains("camera")) {
            tips.add("成都有哪些适合拍照的点位？");
            tips.add("这些点位怎么安排半日路线？");
        } else if (question.contains("雨") || normalized.contains("rain")) {
            tips.add("雨天成都有哪些室内可逛点？");
            tips.add("雨天路线怎么减少步行？");
        } else if (question.contains("亲子") || question.contains("孩子")
                || normalized.contains("family") || normalized.contains("kids")) {
            tips.add("亲子行程适合哪些博物馆？");
            tips.add("带孩子出行怎样控制步行强度？");
        } else {
            tips.add("可以按我的偏好生成一条路线吗？");
            tips.add("这条路线里哪个点最值得久留？");
        }
        return tips;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "" : value.trim();
    }

    private ChatGenerationResult applyFactGuard(String answer, List<ChatGeoSkillService.GeoFact> facts) {
        if (chatFactGuardService == null) {
            return new ChatGenerationResult(answer, Collections.emptyList(), null);
        }
        ChatFactGuardService.GuardResult guardResult = chatFactGuardService.guard(answer, facts);
        return new ChatGenerationResult(guardResult.answer(), guardResult.evidence(), null);
    }

    private ChatGenerationResult tryDirectSkillAnswer(ChatReqDTO req,
                                                      Consumer<String> tokenConsumer,
                                                      ChatRouteContextSkillService.RouteContext routeContext,
                                                      Set<String> usedSkills) {
        ChatSegmentTransportSkillService.SkillResult segmentResult = chatSegmentTransportSkillService == null
                ? null
                : chatSegmentTransportSkillService.tryHandle(req, routeContext);
        if (segmentResult != null && StringUtils.hasText(segmentResult.answer())) {
            usedSkills.addAll(segmentResult.usedSkills());
            if (tokenConsumer != null) {
                tokenConsumer.accept(segmentResult.answer());
            }
            ChatGenerationResult guarded = applyFactGuard(segmentResult.answer(), collectRouteFacts(req));
            ChatFirstLegEtaSkillService.FirstLegEstimate firstLegEstimate = chatFirstLegEtaSkillService == null
                    ? null
                    : chatFirstLegEtaSkillService.estimate(req, routeContext);
            List<String> skillEvidence = mergeSkillEvidence(
                    buildRouteContextEvidence(routeContext),
                    chatSegmentTransportSkillService == null
                            ? List.of()
                            : chatSegmentTransportSkillService.buildEvidence(routeContext, firstLegEstimate),
                    chatFirstLegEtaSkillService == null
                            ? List.of()
                            : chatFirstLegEtaSkillService.buildEvidence(req, routeContext)
            );
            return new ChatGenerationResult(
                    guarded.answer(),
                    mergeEvidence(
                            guarded.evidence(),
                            routeContext,
                            usedSkills,
                            segmentResult.source(),
                            skillEvidence
                    ),
                    null
            );
        }

        ChatFirstLegEtaSkillService.SkillResult firstLegResult = chatFirstLegEtaSkillService == null
                ? null
                : chatFirstLegEtaSkillService.tryHandle(req, routeContext);
        if (firstLegResult != null && StringUtils.hasText(firstLegResult.answer())) {
            usedSkills.addAll(firstLegResult.usedSkills());
            if (tokenConsumer != null) {
                tokenConsumer.accept(firstLegResult.answer());
            }
            ChatGenerationResult guarded = applyFactGuard(firstLegResult.answer(), collectRouteFacts(req));
            List<String> skillEvidence = mergeSkillEvidence(
                    buildRouteContextEvidence(routeContext),
                    chatFirstLegEtaSkillService == null
                            ? List.of()
                            : chatFirstLegEtaSkillService.buildEvidence(req, routeContext)
            );
            return new ChatGenerationResult(
                    guarded.answer(),
                    mergeEvidence(
                            guarded.evidence(),
                            routeContext,
                            usedSkills,
                            firstLegResult.source(),
                            skillEvidence
                    ),
                    null
            );
        }
        return null;
    }

    private ChatGenerationResult trySkillRouterAnswer(ChatReqDTO req,
                                                     Consumer<String> tokenConsumer,
                                                     ChatRouteContextSkillService.RouteContext routeContext,
                                                     Set<String> usedSkills) {
        if (skillRouterService == null) {
            log.warn("Skill router service is not injected; skipping local skill routing.");
            return null;
        }
        log.info("Trying local skill router. question='{}', routeContextAvailable={}",
                req == null ? "" : safe(req.getQuestion()),
                routeContext != null && routeContext.available());
        java.util.Optional<ChatSkillPayloadVO> routed = skillRouterService.route(req);
        if (routed.isEmpty()) {
            log.info("Local skill router returned empty.");
            return null;
        }
        ChatSkillPayloadVO payload = routed.get();
        if (payload == null) {
            log.warn("Local skill router returned null payload.");
            return null;
        }
        log.info("Local skill router selected skillName={}, status={}, messageType={}, workflowType={}",
                payload.getSkillName(),
                payload.getStatus(),
                payload.getMessageType(),
                payload.getWorkflowType());

        String skillName = StringUtils.hasText(payload.getSkillName()) ? payload.getSkillName().trim() : "local-skill";
        usedSkills.add(skillName);
        List<String> skillEvidence = mergeSkillEvidence(
                payload.getEvidence(),
                List.of("skill:" + skillName),
                buildRouteContextEvidence(routeContext)
        );
        List<String> mergedEvidence = mergeEvidence(
                Collections.emptyList(),
                routeContext,
                usedSkills,
                payload.getSource(),
                skillEvidence
        );

        if (requiresClarification(payload)) {
            String clarification = resolveSkillFallbackMessage(payload, "请先补充更明确的位置，我再继续帮你查。");
            if (tokenConsumer != null) {
                tokenConsumer.accept(clarification);
            }
            return new ChatGenerationResult(clarification, mergedEvidence, payload);
        }

        if (shouldBypassSkillSummaryModel(payload)) {
            String workflowMessage = resolveSkillFallbackMessage(payload, "我先按当前找到的方案整理好了，你确认后我再帮你正式应用。");
            if (tokenConsumer != null) {
                tokenConsumer.accept(workflowMessage);
            }
            return new ChatGenerationResult(workflowMessage, mergedEvidence, payload);
        }

        if (payload.getResults() == null || payload.getResults().isEmpty()) {
            String fallback = resolveSkillFallbackMessage(payload, "暂时没有查到合适结果。");
            if (tokenConsumer != null) {
                tokenConsumer.accept(fallback);
            }
            return new ChatGenerationResult(fallback, mergedEvidence, payload);
        }

        try {
            LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
            List<OpenAiGatewayClient.OpenAiMessage> messages = new ArrayList<>();
            messages.add(new OpenAiGatewayClient.OpenAiMessage("system", safePromptBuilder.buildChatSystemPrompt()));
            messages.add(new OpenAiGatewayClient.OpenAiMessage("user", safePromptBuilder.buildSkillGroundedUserPrompt(req, payload)));
            String answer = openAiGatewayClient.request(chatOptions, llmProperties.getOpenai().getApiKey(), messages);
            if (!StringUtils.hasText(answer)) {
                throw new IllegalStateException("OpenAI returned empty skill summary");
            }
            String trimmedAnswer = answer.trim();
            if (tokenConsumer != null) {
                tokenConsumer.accept(trimmedAnswer);
            }
            return new ChatGenerationResult(trimmedAnswer, mergedEvidence, payload);
        } catch (RuntimeException ex) {
            String fallback = resolveSkillFallbackMessage(payload, "我先把查到的结果直接发给你。");
            if (tokenConsumer != null) {
                tokenConsumer.accept(fallback);
            }
            return new ChatGenerationResult(fallback, mergedEvidence, payload);
        }
    }

    private boolean requiresClarification(ChatSkillPayloadVO payload) {
        return payload != null
                && StringUtils.hasText(payload.getStatus())
                && "clarification_required".equalsIgnoreCase(payload.getStatus().trim());
    }

    private boolean shouldBypassSkillSummaryModel(ChatSkillPayloadVO payload) {
        return payload != null
                && "workflow".equalsIgnoreCase(safe(payload.getMessageType()))
                && StringUtils.hasText(payload.getFallbackMessage());
    }

    private String resolveSkillFallbackMessage(ChatSkillPayloadVO payload, String defaultMessage) {
        if (payload != null && StringUtils.hasText(payload.getFallbackMessage())) {
            return payload.getFallbackMessage().trim();
        }
        return defaultMessage;
    }

    private List<String> buildRouteContextEvidence(ChatRouteContextSkillService.RouteContext routeContext) {
        if (routeContext == null || !routeContext.available()) {
            return List.of();
        }
        List<String> evidence = new ArrayList<>();
        ChatReqDTO.ChatRouteNode firstNode = routeContext.firstNode();
        if (firstNode != null && StringUtils.hasText(firstNode.getPoiName())) {
            evidence.add("route_first_stop=" + firstNode.getPoiName().trim());
        }

        List<ChatReqDTO.ChatRouteNode> nodes = routeContext.nodes();
        evidence.add("route_leg_count=" + nodes.size());
        if (!nodes.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (ChatReqDTO.ChatRouteNode node : nodes) {
                if (node == null || !StringUtils.hasText(node.getPoiName())) {
                    continue;
                }
                names.add(node.getPoiName().trim());
                if (names.size() >= 8) {
                    break;
                }
            }
            if (!names.isEmpty()) {
                evidence.add("route_path=" + String.join("->", names));
            }
        }
        return evidence;
    }

    private List<String> mergeSkillEvidence(List<String>... sources) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (sources == null || sources.length == 0) {
            return List.of();
        }
        for (List<String> source : sources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            for (String item : source) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                merged.add(item.trim());
                if (merged.size() >= 12) {
                    return new ArrayList<>(merged);
                }
            }
        }
        return merged.isEmpty() ? List.of() : new ArrayList<>(merged);
    }

    private List<String> mergeEvidence(List<String> guardEvidence,
                                       ChatRouteContextSkillService.RouteContext routeContext,
                                       Set<String> usedSkills,
                                       String firstLegSource,
                                       List<String> skillEvidence) {
        if (chatEvidenceSkillService == null) {
            return guardEvidence == null ? Collections.emptyList() : guardEvidence;
        }
        return chatEvidenceSkillService.mergeEvidence(
                guardEvidence,
                routeContext,
                usedSkills,
                firstLegSource,
                skillEvidence
        );
    }

    private List<ChatGeoSkillService.GeoFact> collectRouteFacts(ChatReqDTO req) {
        if (req == null || req.getContext() == null) {
            return Collections.emptyList();
        }
        List<ChatGeoSkillService.GeoFact> facts = new ArrayList<>();

        if (req.getContext().getItinerary() != null && req.getContext().getItinerary().getNodes() != null) {
            for (ChatReqDTO.ChatRouteNode node : req.getContext().getItinerary().getNodes()) {
                if (node == null || !StringUtils.hasText(node.getPoiName())) {
                    continue;
                }
                facts.add(new ChatGeoSkillService.GeoFact(
                        node.getPoiName(),
                        node.getCategory(),
                        req.getContext().getCityName(),
                        node.getDistrict(),
                        node.getLatitude(),
                        node.getLongitude(),
                        null,
                        "itinerary-route"
                ));
            }
        }

        if (req.getContext().getRecentPois() != null) {
            for (ChatReqDTO.ChatRecentPoi poi : req.getContext().getRecentPois()) {
                if (poi == null || !StringUtils.hasText(poi.getPoiName())) {
                    continue;
                }
                facts.add(new ChatGeoSkillService.GeoFact(
                        poi.getPoiName(),
                        poi.getCategory(),
                        req.getContext().getCityName(),
                        poi.getDistrict(),
                        null,
                        null,
                        null,
                        "recent-poi"
                ));
            }
        }
        return facts;
    }

    private List<ChatGeoSkillService.GeoFact> mergeFactsForGuard(List<ChatGeoSkillService.GeoFact> primary,
                                                                 List<ChatGeoSkillService.GeoFact> secondary) {
        if ((primary == null || primary.isEmpty()) && (secondary == null || secondary.isEmpty())) {
            return Collections.emptyList();
        }
        Map<String, ChatGeoSkillService.GeoFact> merged = new LinkedHashMap<>();
        if (primary != null) {
            for (ChatGeoSkillService.GeoFact fact : primary) {
                addFact(merged, fact);
            }
        }
        if (secondary != null) {
            for (ChatGeoSkillService.GeoFact fact : secondary) {
                addFact(merged, fact);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private void addFact(Map<String, ChatGeoSkillService.GeoFact> merged,
                         ChatGeoSkillService.GeoFact fact) {
        if (fact == null || !StringUtils.hasText(fact.name())) {
            return;
        }
        String key = fact.name().trim().toLowerCase(Locale.ROOT);
        merged.putIfAbsent(key, fact);
    }

    private record ChatGenerationResult(String answer, List<String> evidence, ChatSkillPayloadVO skillPayload) {
        private ChatGenerationResult {
            evidence = evidence == null ? Collections.emptyList() : evidence;
        }
    }
}
