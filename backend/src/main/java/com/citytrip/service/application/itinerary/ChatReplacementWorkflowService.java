package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatReplacementWorkflowService {

    private static final Pattern EXPLICIT_REPLACE_PATTERN = Pattern.compile("(?:\\u628A)?(.+?)(?:\\u66FF\\u6362\\u6210|\\u6362\\u6210)(.+)");
    private static final Pattern ORDINAL_STOP_PATTERN = Pattern.compile("^第([一二三四五六七八九十两\\d]+)(?:站|个点|个景点|个地点)?$");
    private static final String WORKFLOW_TYPE = "itinerary_replace";
    private static final String MESSAGE_TYPE = "workflow";
    private static final String MODE_ONE_TO_ONE = "one_to_one";
    private static final int LIVE_SEARCH_LIMIT = 8;
    private static final double DISTANCE_FILTER_KM_DEFAULT = 12D;
    private static final double DISTANCE_FILTER_KM_NEARBY = 6D;
    private static final double DISTANCE_FILTER_KM_STRICT_NEARBY = 4.5D;
    private static final double DISTANCE_FILTER_KM_MAX = 18D;
    private static final double EARTH_RADIUS_KM = 6371.0088D;

    private final PoiService poiService;
    private final ChatReplacementSessionStore sessionStore;

    public ChatReplacementWorkflowService(PoiService poiService,
                                          ChatReplacementSessionStore sessionStore) {
        this.poiService = poiService;
        this.sessionStore = sessionStore;
    }

    public ChatSkillPayloadVO handle(ChatReqDTO req) {
        if (isRegenerateAction(req)) {
            return regenerateProposal(req);
        }
        if (!hasItineraryContext(req)) {
            return buildClarificationPayload(req, "\u5f53\u524d\u8fd8\u6ca1\u6709\u53ef\u8c03\u6574\u7684\u884c\u7a0b\uff0c\u5148\u751f\u6210\u8def\u7ebf\u540e\u6211\u518d\u5e2e\u4f60\u66ff\u6362\u3002", List.of());
        }

        ExplicitReplaceIntent explicitIntent = resolveExplicitIntent(req);
        if (explicitIntent != null) {
            return buildProposal(req, explicitIntent.targetNodes(), explicitIntent.destinationKeyword());
        }

        String visitKeyword = resolveVisitKeyword(req == null ? null : req.getQuestion());
        if (StringUtils.hasText(visitKeyword)) {
            return buildClarificationPayload(
                    req,
                    buildMissingTargetMessage(currentNodes(req), visitKeyword),
                    currentNodes(req)
            );
        }
        return buildClarificationPayload(
                req,
                "\u4f60\u60f3\u6362\u6389\u54ea\u4e00\u7ad9\uff0c\u6216\u8005\u60f3\u6362\u6210\u4ec0\u4e48\u5730\u65b9\uff1f\u4f60\u53ef\u4ee5\u76f4\u63a5\u8bf4\u201c\u628a A \u6362\u6210 B\u201d\u3002",
                currentNodes(req)
        );
    }

    private ChatSkillPayloadVO regenerateProposal(ChatReqDTO req) {
        String clientSessionId = clientSessionIdOf(req);
        String proposalToken = req == null || req.getAction() == null ? null : req.getAction().getProposalToken();
        Optional<ChatReplacementSessionStore.PendingProposal> storedOptional = sessionStore.getPendingProposal(clientSessionId, proposalToken);
        if (storedOptional.isEmpty()) {
            return buildClarificationPayload(req, "\u4e0a\u4e00\u6761\u66ff\u6362\u65b9\u6848\u5df2\u7ecf\u5931\u6548\u4e86\uff0c\u4f60\u518d\u8bf4\u4e00\u6b21\u60f3\u6362\u54ea\u4e00\u7ad9\u548c\u6362\u6210\u54ea\u91cc\u5427\u3002", currentNodes(req));
        }
        ChatReplacementSessionStore.PendingProposal stored = storedOptional.get();
        List<PoiSearchResultVO> candidates = safeCandidates(stored.getCandidates());
        if (candidates.isEmpty()) {
            return buildClarificationPayload(req, "\u8fd9\u6b21\u6ca1\u6709\u66f4\u591a\u5907\u9009\u65b9\u6848\u4e86\uff0c\u4f60\u53ef\u4ee5\u6362\u4e2a\u76ee\u6807\u5730\u70b9\u518d\u8bd5\u8bd5\u3002", currentNodes(req));
        }
        int nextIndex = candidates.size() <= 1 ? 0 : (stored.getCandidateIndex() + 1) % candidates.size();
        ChatReplacementSessionStore.PendingProposal nextProposal = copyProposal(stored, nextIndex);
        nextProposal.setProposalToken(sessionStore.nextProposalToken());
        sessionStore.savePendingProposal(nextProposal);
        return buildProposalPayload(nextProposal);
    }

    private ChatSkillPayloadVO buildProposal(ChatReqDTO req,
                                             List<ChatReqDTO.ChatRouteNode> targetNodes,
                                             String destinationKeyword) {
        String cityName = req != null && req.getContext() != null ? req.getContext().getCityName() : null;
        List<PoiSearchResultVO> rawCandidates = safeCandidates(poiService.searchLive(destinationKeyword, cityName, LIVE_SEARCH_LIMIT));
        List<PoiSearchResultVO> candidates = rankCandidatesForReplacement(
                rawCandidates,
                targetNodes,
                destinationKeyword,
                req == null ? null : req.getQuestion()
        );
        if (candidates.isEmpty()) {
            ChatSkillPayloadVO payload = basePayload(req);
            payload.setStatus("empty");
            payload.setWorkflowState("proposal_empty");
            payload.setFallbackMessage("\u6211\u6682\u65f6\u6ca1\u641c\u5230\u201c" + destinationKeyword + "\u201d\u8fd9\u4e2a\u5730\u70b9\uff0c\u4f60\u53ef\u4ee5\u6362\u4e2a\u66f4\u5b8c\u6574\u7684\u540d\u5b57\u8bd5\u8bd5\u3002");
            return payload;
        }

        ChatReplacementSessionStore.PendingProposal proposal = new ChatReplacementSessionStore.PendingProposal();
        proposal.setClientSessionId(clientSessionIdOf(req));
        proposal.setProposalToken(sessionStore.nextProposalToken());
        proposal.setItineraryId(req != null && req.getContext() != null && req.getContext().getItinerary() != null
                ? req.getContext().getItinerary().getItineraryId()
                : null);
        proposal.setCityName(cityName);
        proposal.setQuestion(req == null ? null : req.getQuestion());
        proposal.setMode(MODE_ONE_TO_ONE);
        proposal.setTargetPoiIds(targetNodes.stream().map(ChatReqDTO.ChatRouteNode::getPoiId).filter(Objects::nonNull).toList());
        proposal.setTargetPoiNames(targetNodes.stream().map(ChatReqDTO.ChatRouteNode::getPoiName).filter(StringUtils::hasText).toList());
        proposal.setTargetNodes(targetNodes);
        proposal.setCandidates(candidates);
        proposal.setCandidateIndex(0);
        sessionStore.savePendingProposal(proposal);
        return buildProposalPayload(proposal);
    }

    private ChatSkillPayloadVO buildProposalPayload(ChatReplacementSessionStore.PendingProposal proposal) {
        PoiSearchResultVO selected = proposal.getCandidates().get(Math.max(0, Math.min(proposal.getCandidateIndex(), proposal.getCandidates().size() - 1)));
        ChatSkillPayloadVO payload = basePayload(null);
        payload.setWorkflowState("proposal_ready");
        payload.setStatus("ok");
        payload.setClientSessionId(proposal.getClientSessionId());
        payload.setProposalToken(proposal.getProposalToken());
        payload.setResults(toResultItems(proposal.getCandidates()));
        payload.setFallbackMessage("\u6211\u7ed9\u4f60\u627e\u5230\u4e86\u4e00\u4e2a\u66ff\u6362\u65b9\u6848\uff1a\u628a "
                + String.join("\u3001", proposal.getTargetPoiNames())
                + " \u6362\u6210 "
                + selected.name()
                + "\u3002\u6211\u4f1a\u57fa\u4e8e\u5f53\u524d\u8def\u7ebf\u91cd\u65b0\u6574\u7406\u987a\u8def\u5b89\u6392\uff0c\u8981\u4e0d\u8981\u76f4\u63a5\u66ff\u6362\uff1f");

        ChatSkillPayloadVO.ReplacementProposal replacementProposal = new ChatSkillPayloadVO.ReplacementProposal();
        replacementProposal.setMode(proposal.getMode());
        replacementProposal.setTargetPoiIds(proposal.getTargetPoiIds());
        replacementProposal.setTargetPoiNames(proposal.getTargetPoiNames());
        replacementProposal.setReplacementPoiNames(List.of(selected.name()));
        payload.setReplacementProposal(replacementProposal);

        ChatSkillPayloadVO.ProposalSummary summary = new ChatSkillPayloadVO.ProposalSummary();
        summary.setTitle("\u672c\u6b21\u5c06\u5982\u4f55\u4fee\u6539");
        summary.setDetail("\u628a " + String.join("\u3001", proposal.getTargetPoiNames()) + " \u6362\u6210 " + selected.name());
        payload.setProposalSummary(summary);
        payload.setItineraryEditDraft(buildReplacementDraft(proposal, selected));
        payload.setFallbackMessage(buildReplacementSummary(proposal, selected));
        payload.setActions(List.of(
                action("confirm_replacement", "\u786e\u8ba4\u66f4\u6362", "primary", null),
                action("regenerate_replacement", "\u6362\u4e2a\u65b9\u6848", "secondary", null),
                action("decline_replacement", "\u5148\u4e0d\u66f4\u6362", "ghost", null)
        ));
        return payload;
    }

    private ChatSkillPayloadVO buildClarificationPayload(ChatReqDTO req,
                                                         String message,
                                                         List<ChatReqDTO.ChatRouteNode> nodes) {
        ChatSkillPayloadVO payload = basePayload(req);
        payload.setStatus("clarification_required");
        payload.setWorkflowState("clarification_required");
        payload.setFallbackMessage(message);
        payload.setClarificationOptions(safeNodes(nodes).stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getPoiName()))
                .map(node -> {
                    ChatSkillPayloadVO.ClarificationOption option = new ChatSkillPayloadVO.ClarificationOption();
                    option.setKey(String.valueOf(node.getPoiId()));
                    option.setLabel(node.getPoiName().trim());
                    option.setValue(node.getPoiName().trim());
                    return option;
                })
                .collect(Collectors.toList()));
        return payload;
    }

    private ChatSkillPayloadVO basePayload(ChatReqDTO req) {
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName(WORKFLOW_TYPE);
        payload.setMessageType(MESSAGE_TYPE);
        payload.setWorkflowType(WORKFLOW_TYPE);
        payload.setSource("local-workflow");
        payload.setClientSessionId(clientSessionIdOf(req));
        return payload;
    }

    private ChatReplacementSessionStore.PendingProposal copyProposal(ChatReplacementSessionStore.PendingProposal source,
                                                                     int nextIndex) {
        ChatReplacementSessionStore.PendingProposal target = new ChatReplacementSessionStore.PendingProposal();
        target.setClientSessionId(source.getClientSessionId());
        target.setItineraryId(source.getItineraryId());
        target.setCityName(source.getCityName());
        target.setQuestion(source.getQuestion());
        target.setMode(source.getMode());
        target.setTargetPoiIds(source.getTargetPoiIds());
        target.setTargetPoiNames(source.getTargetPoiNames());
        target.setTargetNodes(source.getTargetNodes());
        target.setCandidates(source.getCandidates());
        target.setCandidateIndex(nextIndex);
        return target;
    }

    private ChatSkillPayloadVO.ItineraryEditDraft buildReplacementDraft(ChatReplacementSessionStore.PendingProposal proposal,
                                                                        PoiSearchResultVO selected) {
        ChatSkillPayloadVO.ItineraryEditDraft draft = new ChatSkillPayloadVO.ItineraryEditDraft();
        draft.setSummary("\u628a " + String.join("\u3001", proposal.getTargetPoiNames()) + " \u6362\u6210 " + selected.name());

        List<ItineraryEditOperationDTO> operations = new ArrayList<>();
        for (ChatReqDTO.ChatRouteNode targetNode : safeNodes(proposal.getTargetNodes())) {
            if (!StringUtils.hasText(targetNode.getNodeKey())) {
                continue;
            }
            ItineraryEditOperationDTO remove = new ItineraryEditOperationDTO();
            remove.setType("remove_node");
            remove.setNodeKey(targetNode.getNodeKey().trim());
            operations.add(remove);
        }

        ChatReqDTO.ChatRouteNode anchor = safeNodes(proposal.getTargetNodes()).stream()
                .filter(Objects::nonNull)
                .min(Comparator
                        .comparingInt((ChatReqDTO.ChatRouteNode node) -> node.getDayNo() == null ? 1 : node.getDayNo())
                        .thenComparingInt(node -> node.getStepOrder() == null ? 1 : node.getStepOrder()))
                .orElse(null);
        if (anchor != null) {
            ItineraryEditOperationDTO insert = new ItineraryEditOperationDTO();
            insert.setType("insert_inline_custom_poi");
            insert.setDayNo(anchor.getDayNo() == null ? 1 : anchor.getDayNo());
            insert.setTargetIndex(anchor.getStepOrder() == null ? 1 : anchor.getStepOrder());
            insert.setStayDuration(anchor.getStayDuration() == null ? 90 : Math.max(anchor.getStayDuration(), 0));
            ItineraryEditOperationDTO.CustomPoiDraft customPoiDraft = new ItineraryEditOperationDTO.CustomPoiDraft();
            customPoiDraft.setName(selected.name());
            customPoiDraft.setRoughLocation(StringUtils.hasText(selected.address()) ? selected.address() : selected.name());
            customPoiDraft.setCategory(selected.category());
            customPoiDraft.setReason("\u804a\u5929\u66ff\u6362\u5efa\u8bae");
            customPoiDraft.setAddress(selected.address());
            customPoiDraft.setLatitude(selected.latitude());
            customPoiDraft.setLongitude(selected.longitude());
            customPoiDraft.setGeoSource(selected.source());
            insert.setCustomPoiDraft(customPoiDraft);
            operations.add(insert);
        }

        draft.setOperations(operations);
        return draft;
    }

    private String buildReplacementSummary(ChatReplacementSessionStore.PendingProposal proposal,
                                           PoiSearchResultVO selected) {
        String targetNames = String.join("\u3001", proposal.getTargetPoiNames());
        String address = StringUtils.hasText(selected.address()) ? "\uff08" + selected.address() + "\uff09" : "";
        return "\u672c\u6b21\u5c06\u8fd9\u6837\u4fee\u6539\uff1a\n"
                + "1. \u79fb\u9664\u5f53\u524d\u884c\u7a0b\u4e2d\u7684\u300c" + targetNames + "\u300d\u3002\n"
                + "2. \u5728\u539f\u4f4d\u7f6e\u63d2\u5165\u300c" + selected.name() + "\u300d" + address + "\u3002\n"
                + "3. \u518d\u6309\u65b0\u7684\u5730\u70b9\u91cd\u65b0\u6574\u7406\u987a\u8def\u987a\u5e8f\u548c\u65f6\u95f4\u5b89\u6392\u3002\n"
                + "\u5982\u679c\u4f60\u540c\u610f\uff0c\u6211\u5c31\u76f4\u63a5\u66ff\u6362\u3002";
    }

    private ExplicitReplaceIntent resolveExplicitIntent(ChatReqDTO req) {
        String question = req == null ? null : req.getQuestion();
        if (!StringUtils.hasText(question)) {
            return null;
        }
        Matcher matcher = EXPLICIT_REPLACE_PATTERN.matcher(question.trim());
        if (!matcher.find()) {
            return null;
        }
        String targetKeyword = trimDecorators(matcher.group(1));
        String destinationKeyword = trimDecorators(matcher.group(2));
        if (!StringUtils.hasText(targetKeyword) || !StringUtils.hasText(destinationKeyword)) {
            return null;
        }
        List<ChatReqDTO.ChatRouteNode> matchedNodes = resolveTargetNodes(targetKeyword, currentNodes(req));
        if (matchedNodes.isEmpty()) {
            return null;
        }
        return new ExplicitReplaceIntent(matchedNodes, destinationKeyword);
    }

    private String buildMissingTargetMessage(List<ChatReqDTO.ChatRouteNode> nodes, String destinationKeyword) {
        List<String> names = safeNodes(nodes).stream()
                .filter(Objects::nonNull)
                .map(ChatReqDTO.ChatRouteNode::getPoiName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (names.isEmpty()) {
            return "\u6211\u77e5\u9053\u4f60\u60f3\u53bb" + destinationKeyword + "\uff0c\u4f46\u5f53\u524d\u8fd8\u6ca1\u6709\u53ef\u66ff\u6362\u7684\u7ad9\u70b9\u3002";
        }
        return "\u6211\u77e5\u9053\u4f60\u60f3\u53bb" + destinationKeyword + "\u3002\u4f60\u60f3\u6362\u6389\u54ea\u4e00\u7ad9\uff1f\u5f53\u524d\u884c\u7a0b\u91cc\u53ef\u4ee5\u66ff\u6362\u7684\u6709\uff1a" + String.join("\u3001", names) + "\u3002";
    }

    private String resolveVisitKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String normalized = question.trim();
        String[] prefixes = {"\u6211\u60f3\u53bb", "\u60f3\u53bb", "\u6211\u60f3\u901b", "\u60f3\u901b", "\u53bb", "\u770b\u770b", "\u6253\u5361"};
        String[] suffixes = {"\u73a9\u4e00\u73a9", "\u901b\u4e00\u901b", "\u73a9", "\u901b", "\u770b\u770b", "\u6253\u5361", "\u65c5\u6e38", "\u62cd\u7167", "\u5417", "\u5440", "\u554a", "\u5462"};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                String candidate = normalized.substring(prefix.length()).trim();
                boolean changed;
                do {
                    changed = false;
                    for (String suffix : suffixes) {
                        if (candidate.endsWith(suffix)) {
                            candidate = candidate.substring(0, candidate.length() - suffix.length()).trim();
                            changed = true;
                            break;
                        }
                    }
                } while (changed && !candidate.isEmpty());
                if (candidate.length() >= 2 && candidate.length() <= 24) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean hasItineraryContext(ChatReqDTO req) {
        return !currentNodes(req).isEmpty();
    }

    private boolean isRegenerateAction(ChatReqDTO req) {
        return req != null
                && req.getAction() != null
                && "regenerate_replacement".equalsIgnoreCase(req.getAction().getType());
    }

    private String clientSessionIdOf(ChatReqDTO req) {
        if (req != null && req.getAction() != null && StringUtils.hasText(req.getAction().getClientSessionId())) {
            return req.getAction().getClientSessionId().trim();
        }
        return "chat-replacement-default";
    }

    private List<ChatReqDTO.ChatRouteNode> currentNodes(ChatReqDTO req) {
        if (req == null || req.getContext() == null || req.getContext().getItinerary() == null || req.getContext().getItinerary().getNodes() == null) {
            return Collections.emptyList();
        }
        return req.getContext().getItinerary().getNodes();
    }

    private List<PoiSearchResultVO> safeCandidates(List<PoiSearchResultVO> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private List<ChatReqDTO.ChatRouteNode> safeNodes(List<ChatReqDTO.ChatRouteNode> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private List<PoiSearchResultVO> rankCandidatesForReplacement(List<PoiSearchResultVO> rawCandidates,
                                                                 List<ChatReqDTO.ChatRouteNode> targetNodes,
                                                                 String destinationKeyword,
                                                                 String question) {
        List<PoiSearchResultVO> candidates = safeCandidates(rawCandidates);
        if (candidates.isEmpty()) {
            return candidates;
        }

        ChatReqDTO.ChatRouteNode anchor = resolveAnchorNode(targetNodes);
        double distanceThresholdKm = resolveDistanceThresholdKm(destinationKeyword, question);
        String normalizedKeyword = normalizeText(destinationKeyword);
        Set<String> interestTokens = resolveInterestTokens(destinationKeyword, question);

        List<CandidateRank> rankedPool = new ArrayList<>();
        for (PoiSearchResultVO candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.name())) {
                continue;
            }
            Double distanceKm = resolveDistanceKm(anchor, candidate);
            double score = scoreCandidate(candidate, normalizedKeyword, interestTokens, distanceKm, distanceThresholdKm);
            rankedPool.add(new CandidateRank(candidate, distanceKm, score));
        }
        if (rankedPool.isEmpty()) {
            return Collections.emptyList();
        }

        List<CandidateRank> filteredPool = rankedPool;
        if (hasCoordinates(anchor)) {
            filteredPool = rankedPool.stream()
                    .filter(rank -> rank.distanceKm() == null || rank.distanceKm() <= distanceThresholdKm)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (filteredPool.isEmpty()) {
                filteredPool = rankedPool;
            }
        }

        filteredPool.sort(Comparator
                .comparingDouble(CandidateRank::score).reversed()
                .thenComparing(rank -> rank.distanceKm() == null ? Double.MAX_VALUE : rank.distanceKm())
                .thenComparing(rank -> normalizeText(rank.poi().name())));

        Set<String> dedupeKeys = new LinkedHashSet<>();
        List<PoiSearchResultVO> ranked = new ArrayList<>();
        for (CandidateRank rank : filteredPool) {
            String dedupeKey = buildCandidateDedupeKey(rank.poi());
            if (dedupeKeys.add(dedupeKey)) {
                ranked.add(rank.poi());
            }
        }
        return ranked;
    }

    private double scoreCandidate(PoiSearchResultVO candidate,
                                  String normalizedKeyword,
                                  Set<String> interestTokens,
                                  Double distanceKm,
                                  double distanceThresholdKm) {
        double score = keywordAffinityScore(candidate, normalizedKeyword) * 3.2D;
        score += interestAffinityScore(candidate, interestTokens) * 2.4D;
        if (distanceKm != null) {
            double proximity = Math.max(0D, (distanceThresholdKm - distanceKm) / Math.max(0.1D, distanceThresholdKm));
            score += proximity * 2.1D;
        }
        String source = normalizeText(candidate.source());
        if (source.contains("vivo") || source.contains("geo")) {
            score += 0.2D;
        }
        return score;
    }

    private double keywordAffinityScore(PoiSearchResultVO candidate, String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return 0D;
        }
        String candidateText = buildCandidateText(candidate);
        double score = 0D;
        if (candidateText.contains(normalizedKeyword) || normalizedKeyword.contains(candidateText)) {
            score += 1.8D;
        }
        for (String term : splitSearchTerms(normalizedKeyword)) {
            if (candidateText.contains(term)) {
                score += 0.6D;
            }
        }
        return Math.min(score, 3.2D);
    }

    private double interestAffinityScore(PoiSearchResultVO candidate, Set<String> interestTokens) {
        if (candidate == null || interestTokens == null || interestTokens.isEmpty()) {
            return 0D;
        }
        String candidateText = buildCandidateText(candidate);
        double score = 0D;
        for (String token : interestTokens) {
            if (candidateText.contains(token)) {
                score += 0.7D;
            }
        }
        return Math.min(score, 3.5D);
    }

    private String buildCandidateText(PoiSearchResultVO candidate) {
        if (candidate == null) {
            return "";
        }
        return normalizeText((candidate.name() == null ? "" : candidate.name())
                + " " + (candidate.category() == null ? "" : candidate.category())
                + " " + (candidate.address() == null ? "" : candidate.address()));
    }

    private Set<String> resolveInterestTokens(String destinationKeyword, String question) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalizedText = normalizeText((destinationKeyword == null ? "" : destinationKeyword)
                + " " + (question == null ? "" : question));
        if (!StringUtils.hasText(normalizedText)) {
            return tokens;
        }

        if (containsAny(normalizedText, "安静", "清静", "静谧", "安逸")) {
            tokens.add("安静");
            tokens.add("清静");
            tokens.add("书店");
            tokens.add("咖啡");
            tokens.add("公园");
            tokens.add("博物馆");
        }
        if (containsAny(normalizedText, "咖啡", "咖啡馆", "cafe", "coffee")) {
            tokens.add("咖啡");
            tokens.add("咖啡馆");
            tokens.add("cafe");
            tokens.add("coffee");
        }
        if (containsAny(normalizedText, "亲子", "带娃", "儿童", "小朋友", "宝宝", "family")) {
            tokens.add("亲子");
            tokens.add("儿童");
            tokens.add("乐园");
            tokens.add("动物园");
            tokens.add("海洋馆");
            tokens.add("公园");
        }
        if (containsAny(normalizedText, "酒店", "宾馆", "住宿", "hotel")) {
            tokens.add("酒店");
            tokens.add("宾馆");
            tokens.add("住宿");
            tokens.add("hotel");
        }

        tokens.addAll(splitSearchTerms(normalizedText));
        return tokens;
    }

    private double resolveDistanceThresholdKm(String destinationKeyword, String question) {
        String text = normalizeText((destinationKeyword == null ? "" : destinationKeyword)
                + " " + (question == null ? "" : question));
        if (containsAny(text, "附近", "周边", "旁边", "就近", "nearby")) {
            return DISTANCE_FILTER_KM_STRICT_NEARBY;
        }
        if (containsAny(text, "安静", "咖啡", "亲子", "酒店", "住宿")) {
            return DISTANCE_FILTER_KM_NEARBY;
        }
        return DISTANCE_FILTER_KM_DEFAULT;
    }

    private ChatReqDTO.ChatRouteNode resolveAnchorNode(List<ChatReqDTO.ChatRouteNode> targetNodes) {
        return safeNodes(targetNodes).stream()
                .filter(Objects::nonNull)
                .min(Comparator
                        .comparingInt((ChatReqDTO.ChatRouteNode node) -> node.getDayNo() == null ? Integer.MAX_VALUE : node.getDayNo())
                        .thenComparingInt(node -> node.getStepOrder() == null ? Integer.MAX_VALUE : node.getStepOrder()))
                .orElse(null);
    }

    private Double resolveDistanceKm(ChatReqDTO.ChatRouteNode anchor, PoiSearchResultVO candidate) {
        if (!hasCoordinates(anchor) || candidate == null || candidate.latitude() == null || candidate.longitude() == null) {
            return null;
        }
        return haversineKm(anchor.getLatitude(), anchor.getLongitude(), candidate.latitude(), candidate.longitude());
    }

    private boolean hasCoordinates(ChatReqDTO.ChatRouteNode node) {
        return node != null && node.getLatitude() != null && node.getLongitude() != null;
    }

    private double haversineKm(BigDecimal fromLat,
                               BigDecimal fromLng,
                               BigDecimal toLat,
                               BigDecimal toLng) {
        double lat1 = Math.toRadians(fromLat.doubleValue());
        double lon1 = Math.toRadians(fromLng.doubleValue());
        double lat2 = Math.toRadians(toLat.doubleValue());
        double lon2 = Math.toRadians(toLng.doubleValue());
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double sinLat = Math.sin(dLat / 2D);
        double sinLon = Math.sin(dLon / 2D);
        double a = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double c = 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
        return Math.min(DISTANCE_FILTER_KM_MAX * 2D, EARTH_RADIUS_KM * c);
    }

    private List<String> splitSearchTerms(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text.split("[\\s,，。；;、/|]+")).stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }

    private boolean containsAny(String text, String... words) {
        if (!StringUtils.hasText(text) || words == null) {
            return false;
        }
        for (String word : words) {
            if (StringUtils.hasText(word) && text.contains(normalizeText(word))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private String buildCandidateDedupeKey(PoiSearchResultVO candidate) {
        if (candidate == null) {
            return "null";
        }
        String name = normalizeText(candidate.name());
        String lat = candidate.latitude() == null ? "" : candidate.latitude().toPlainString();
        String lng = candidate.longitude() == null ? "" : candidate.longitude().toPlainString();
        return name + "|" + lat + "|" + lng;
    }

    private List<ChatSkillPayloadVO.ResultItem> toResultItems(List<PoiSearchResultVO> candidates) {
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>();
        for (PoiSearchResultVO candidate : safeCandidates(candidates)) {
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(candidate.name());
            item.setAddress(candidate.address());
            item.setCategory(candidate.category());
            item.setLatitude(candidate.latitude());
            item.setLongitude(candidate.longitude());
            item.setCityName(candidate.cityName());
            item.setSource(candidate.source());
            items.add(item);
        }
        return items;
    }

    private ChatSkillPayloadVO.ActionItem action(String key, String label, String style, String value) {
        ChatSkillPayloadVO.ActionItem action = new ChatSkillPayloadVO.ActionItem();
        action.setKey(key);
        action.setLabel(label);
        action.setStyle(style);
        action.setValue(value);
        return action;
    }

    private String trimDecorators(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        while (normalized.startsWith("\u628a") || normalized.startsWith("\u5c06")) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.endsWith("\u5427") || normalized.endsWith("\u5440") || normalized.endsWith("\u5462")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private List<ChatReqDTO.ChatRouteNode> resolveTargetNodes(String targetKeyword,
                                                              List<ChatReqDTO.ChatRouteNode> nodes) {
        List<ChatReqDTO.ChatRouteNode> safeNodes = safeNodes(nodes);
        if (!StringUtils.hasText(targetKeyword) || safeNodes.isEmpty()) {
            return List.of();
        }
        List<ChatReqDTO.ChatRouteNode> matchedByName = safeNodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getPoiName()))
                .filter(node -> node.getPoiName().contains(targetKeyword) || targetKeyword.contains(node.getPoiName()))
                .toList();
        if (!matchedByName.isEmpty()) {
            return matchedByName;
        }

        Integer ordinalIndex = resolveOrdinalIndex(targetKeyword);
        if (ordinalIndex == null || ordinalIndex < 1) {
            return List.of();
        }
        List<ChatReqDTO.ChatRouteNode> orderedNodes = safeNodes.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((ChatReqDTO.ChatRouteNode node) -> node.getDayNo() == null ? Integer.MAX_VALUE : node.getDayNo())
                        .thenComparingInt(node -> node.getStepOrder() == null ? Integer.MAX_VALUE : node.getStepOrder()))
                .toList();
        if (ordinalIndex > orderedNodes.size()) {
            return List.of();
        }
        return List.of(orderedNodes.get(ordinalIndex - 1));
    }

    private Integer resolveOrdinalIndex(String targetKeyword) {
        String normalized = trimDecorators(targetKeyword);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Matcher matcher = ORDINAL_STOP_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        String rawOrdinal = matcher.group(1);
        if (!StringUtils.hasText(rawOrdinal)) {
            return null;
        }
        if (rawOrdinal.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(rawOrdinal);
        }
        return chineseOrdinalToInt(rawOrdinal);
    }

    private Integer chineseOrdinalToInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim()) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            case "十一" -> 11;
            case "十二" -> 12;
            default -> null;
        };
    }

    private record ExplicitReplaceIntent(List<ChatReqDTO.ChatRouteNode> targetNodes,
                                         String destinationKeyword) {
    }

    private record CandidateRank(PoiSearchResultVO poi,
                                 Double distanceKm,
                                 double score) {
    }
}
