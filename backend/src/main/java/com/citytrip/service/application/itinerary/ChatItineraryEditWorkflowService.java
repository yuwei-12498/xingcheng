package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatItineraryEditWorkflowService {

    private static final String WORKFLOW_TYPE = "itinerary_edit";
    private static final String MESSAGE_TYPE = "workflow";
    private static final Pattern START_TIME_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d]+)天[^。；，,]*?(\\d{1,2}:\\d{2})(?:开始|出发)");
    private static final Pattern END_TIME_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d]+)天[^。；，,]*?(\\d{1,2}:\\d{2})(?:结束|收工|返回)");
    private static final Pattern REDUCE_STAY_PATTERN = Pattern.compile("(少玩|少待|缩短)(半小时|\\d+小时半|\\d+小时|\\d+分钟)");
    private static final Pattern INCREASE_STAY_PATTERN = Pattern.compile("(多玩|多待|延长)(半小时|\\d+小时半|\\d+小时|\\d+分钟)");
    private static final Pattern ABSOLUTE_STAY_PATTERN = Pattern.compile("(停留|游玩)?(?:改成|改为)(半小时|\\d+小时半|\\d+小时|\\d+分钟)");
    private static final Pattern STOP_COUNT_PATTERN = Pattern.compile("(?:从)?\\d+站?(?:变成|改成|改为|调整为|到)(\\d+)站?");
    private static final Pattern FRONT_ADD_KEYWORD_PATTERN = Pattern.compile("(?:加入|加上|新增|添加|插入|补上|安排|放入)(?:一个)?(.+?)(?:就行了|就好|即可|吧|呀|呢|，|,|。|；|;|$)");
    private static final Pattern VISIT_INTENT_PATTERN = Pattern.compile("(?:我)?(?:想去|要去|想逛|想看|想打卡|打卡|顺便去|带上|带我去|一定要去|必须去)(.+?)(?:就行了|就好|即可|吧|呀|呢|，|,|。|；|;|$)");
    private static final Pattern KEYWORD_TO_AFTER_PATTERN = Pattern.compile("(?:把)?(.+?)(?:加到|加在|加入到|添加到|放到|放在|插到|插在|安排到|安排在)(.+?)(后面|后边|之后|后)");
    private static final Pattern KEYWORD_TO_BEFORE_PATTERN = Pattern.compile("(?:把)?(.+?)(?:加到|加在|加入到|添加到|放到|放在|插到|插在|安排到|安排在)(.+?)(前面|前边|之前|前)");
    private static final Pattern ADD_AFTER_PATTERN = Pattern.compile("(?:在)?(.+?)(后面|后边|之后|后)\\s*[，,、]?\\s*(?:再)?加(?:上|一个)?(.+)");
    private static final Pattern ADD_BEFORE_PATTERN = Pattern.compile("(?:在)?(.+?)(前面|前边|之前|前)\\s*[，,、]?\\s*(?:再)?加(?:上|一个)?(.+)");
    private static final Pattern ADD_DAY_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d]+)天(?:第(\\d+)站)?(?:加|加入|新增|添加|插入|安排)(?:一个)?(.+)");
    private static final Pattern ADD_TO_ROUTE_PATTERN = Pattern.compile("(?:这条路线|当前路线|行程|路线上|路线里).*?(?:加|加入|新增|添加|插入|补上|安排)(?:上|一个)?(.+)");

    private final PoiService poiService;

    public ChatItineraryEditWorkflowService(PoiService poiService) {
        this.poiService = poiService;
    }

    public ChatSkillPayloadVO handle(ChatReqDTO req) {
        if (!hasItineraryContext(req)) {
            return buildClarificationPayload(req, "当前还没有可调整的行程，先生成路线后我再帮你修改。", List.of());
        }

        DraftBuildResult result = buildDraft(req);
        if (!result.success()) {
            return buildClarificationPayload(req, result.message(), currentNodes(req));
        }

        ChatSkillPayloadVO payload = basePayload();
        payload.setStatus("ok");
        payload.setWorkflowState("proposal_ready");
        payload.setFallbackMessage(result.message());
        payload.setResults(result.results());
        payload.setItineraryEditDraft(result.draft());

        ChatSkillPayloadVO.ProposalSummary summary = new ChatSkillPayloadVO.ProposalSummary();
        summary.setTitle("本次将如何修改");
        summary.setDetail(result.draft().getSummary());
        payload.setProposalSummary(summary);
        payload.setActions(List.of(
                action("confirm_itinerary_edit", "确认修改", "primary"),
                action("decline_itinerary_edit", "先不修改", "ghost")
        ));
        return payload;
    }

    private DraftBuildResult buildDraft(ChatReqDTO req) {
        String question = req == null ? null : normalizeQuestion(req.getQuestion());
        if (!StringUtils.hasText(question)) {
            return DraftBuildResult.clarify("你可以直接告诉我要改哪一天、哪一站，或者要新增哪个地点。");
        }

        List<ItineraryEditOperationDTO> operations = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        List<ChatSkillPayloadVO.ResultItem> results = new ArrayList<>();
        AddOperationResult wholeQuestionAddResult = appendAddOperation(req, question, operations, summaryLines, results);
        if (wholeQuestionAddResult == AddOperationResult.CLARIFY) {
            return DraftBuildResult.clarify("我暂时没搜到这个地点。你可以换个更完整的名字，比如“成都动物园”，或告诉我放在哪一站后面。");
        }
        boolean addAlreadyApplied = wholeQuestionAddResult == AddOperationResult.ADDED;

        for (String clause : splitClauses(question)) {
            if (appendStopCountClarification(req, question, operations)) {
                return DraftBuildResult.clarify(buildStopCountClarification(question, currentNodes(req)));
            }
            if (appendDayWindowOperations(clause, operations, summaryLines)) {
                continue;
            }
            if (appendStayOperation(clause, currentNodes(req), operations, summaryLines)) {
                continue;
            }
            if (appendRemoveOperation(clause, currentNodes(req), operations, summaryLines)) {
                continue;
            }

            if (!addAlreadyApplied) {
                AddOperationResult addResult = appendAddOperation(req, clause, operations, summaryLines, results);
                if (addResult == AddOperationResult.ADDED) {
                    addAlreadyApplied = true;
                    continue;
                }
                if (addResult == AddOperationResult.CLARIFY) {
                    return DraftBuildResult.clarify("我暂时没搜到这个地点。你可以换个更完整的名字，比如“成都动物园”，或告诉我放在哪一站后面。");
                }
            }
        }

        if (operations.isEmpty()) {
            return DraftBuildResult.clarify("我还没看懂你想怎么改。你可以直接说“把 IFS 加到建设路小吃街后面”“把 A 少玩 30 分钟”或“第 2 天 10:00 开始”。");
        }

        ChatSkillPayloadVO.ItineraryEditDraft draft = new ChatSkillPayloadVO.ItineraryEditDraft();
        String summary = String.join("；", summaryLines);
        draft.setSummary(summary);
        draft.setOperations(operations);

        StringBuilder message = new StringBuilder("本次将这样修改：\n");
        for (int index = 0; index < summaryLines.size(); index++) {
            message.append(index + 1).append(". ").append(summaryLines.get(index)).append('\n');
        }
        message.append("如果你同意，我就直接应用到当前行程。");
        return DraftBuildResult.success(draft, message.toString(), results);
    }

    private boolean appendDayWindowOperations(String question,
                                              List<ItineraryEditOperationDTO> operations,
                                              List<String> summaryLines) {
        boolean appended = false;
        Matcher startMatcher = START_TIME_PATTERN.matcher(question);
        while (startMatcher.find()) {
            Integer dayNo = parseDayNo(startMatcher.group(1));
            if (dayNo == null) {
                continue;
            }
            ItineraryEditOperationDTO operation = new ItineraryEditOperationDTO();
            operation.setType("update_day_window");
            operation.setDayNo(dayNo);
            operation.setStartTime(startMatcher.group(2));
            operations.add(operation);
            summaryLines.add("第 " + dayNo + " 天开始时间改为 " + startMatcher.group(2));
            appended = true;
        }

        Matcher endMatcher = END_TIME_PATTERN.matcher(question);
        while (endMatcher.find()) {
            Integer dayNo = parseDayNo(endMatcher.group(1));
            if (dayNo == null) {
                continue;
            }
            ItineraryEditOperationDTO operation = new ItineraryEditOperationDTO();
            operation.setType("update_day_window");
            operation.setDayNo(dayNo);
            operation.setEndTime(endMatcher.group(2));
            operations.add(operation);
            summaryLines.add("第 " + dayNo + " 天结束时间改为 " + endMatcher.group(2));
            appended = true;
        }
        return appended;
    }

    private boolean appendStayOperation(String clause,
                                        List<ChatReqDTO.ChatRouteNode> nodes,
                                        List<ItineraryEditOperationDTO> operations,
                                        List<String> summaryLines) {
        ChatReqDTO.ChatRouteNode node = resolveNodeByMention(clause, nodes);
        if (node == null || !StringUtils.hasText(node.getNodeKey())) {
            return false;
        }

        Matcher reduceMatcher = REDUCE_STAY_PATTERN.matcher(clause);
        if (reduceMatcher.find()) {
            int stayDuration = Math.max(0, defaultStay(node) - parseDurationMinutes(reduceMatcher.group(2)));
            operations.add(buildStayOperation(node, stayDuration));
            summaryLines.add("「" + node.getPoiName() + "」停留调整为 " + stayDuration + " 分钟");
            return true;
        }

        Matcher increaseMatcher = INCREASE_STAY_PATTERN.matcher(clause);
        if (increaseMatcher.find()) {
            int stayDuration = defaultStay(node) + parseDurationMinutes(increaseMatcher.group(2));
            operations.add(buildStayOperation(node, stayDuration));
            summaryLines.add("「" + node.getPoiName() + "」停留调整为 " + stayDuration + " 分钟");
            return true;
        }

        Matcher absoluteMatcher = ABSOLUTE_STAY_PATTERN.matcher(clause);
        if (absoluteMatcher.find()) {
            int stayDuration = Math.max(0, parseDurationMinutes(absoluteMatcher.group(2)));
            operations.add(buildStayOperation(node, stayDuration));
            summaryLines.add("「" + node.getPoiName() + "」停留调整为 " + stayDuration + " 分钟");
            return true;
        }
        return false;
    }

    private boolean appendRemoveOperation(String clause,
                                          List<ChatReqDTO.ChatRouteNode> nodes,
                                          List<ItineraryEditOperationDTO> operations,
                                          List<String> summaryLines) {
        if (!containsAny(clause, "删除", "去掉", "移除", "取消", "不要")) {
            return false;
        }
        ChatReqDTO.ChatRouteNode node = resolveNodeByMention(clause, nodes);
        if (node == null) {
            node = resolveOrdinalNode(clause, nodes);
        }
        if (node == null || !StringUtils.hasText(node.getNodeKey())) {
            return false;
        }
        ItineraryEditOperationDTO operation = new ItineraryEditOperationDTO();
        operation.setType("remove_node");
        operation.setNodeKey(node.getNodeKey().trim());
        operations.add(operation);
        summaryLines.add("移除「" + node.getPoiName() + "」");
        return true;
    }

    private AddOperationResult appendAddOperation(ChatReqDTO req,
                                                  String clause,
                                                  List<ItineraryEditOperationDTO> operations,
                                                  List<String> summaryLines,
                                                  List<ChatSkillPayloadVO.ResultItem> results) {
        String frontKeyword = extractFrontAddKeyword(clause);
        ChatReqDTO.ChatRouteNode afterAnchor = resolveAnchorWithMarker(clause, currentNodes(req), true);
        if (afterAnchor != null && StringUtils.hasText(frontKeyword)) {
            return appendResolvedPoi(req, frontKeyword, afterAnchor.getDayNo(), defaultStep(afterAnchor) + 1, operations, summaryLines, results);
        }

        ChatReqDTO.ChatRouteNode beforeAnchor = resolveAnchorWithMarker(clause, currentNodes(req), false);
        if (beforeAnchor != null && StringUtils.hasText(frontKeyword)) {
            return appendResolvedPoi(req, frontKeyword, beforeAnchor.getDayNo(), defaultStep(beforeAnchor), operations, summaryLines, results);
        }

        Matcher keywordAfterMatcher = KEYWORD_TO_AFTER_PATTERN.matcher(clause);
        if (keywordAfterMatcher.find()) {
            ChatReqDTO.ChatRouteNode anchor = resolveNodeByMention(keywordAfterMatcher.group(2), currentNodes(req));
            if (anchor == null) {
                return AddOperationResult.SKIPPED;
            }
            return appendResolvedPoi(req, cleanupKeyword(keywordAfterMatcher.group(1)), anchor.getDayNo(), defaultStep(anchor) + 1, operations, summaryLines, results);
        }

        Matcher keywordBeforeMatcher = KEYWORD_TO_BEFORE_PATTERN.matcher(clause);
        if (keywordBeforeMatcher.find()) {
            ChatReqDTO.ChatRouteNode anchor = resolveNodeByMention(keywordBeforeMatcher.group(2), currentNodes(req));
            if (anchor == null) {
                return AddOperationResult.SKIPPED;
            }
            return appendResolvedPoi(req, cleanupKeyword(keywordBeforeMatcher.group(1)), anchor.getDayNo(), defaultStep(anchor), operations, summaryLines, results);
        }

        Matcher afterMatcher = ADD_AFTER_PATTERN.matcher(clause);
        if (afterMatcher.find()) {
            ChatReqDTO.ChatRouteNode anchor = resolveNodeByMention(afterMatcher.group(1), currentNodes(req));
            if (anchor == null) {
                return AddOperationResult.SKIPPED;
            }
            return appendResolvedPoi(req, cleanupKeyword(afterMatcher.group(3)), anchor.getDayNo(), defaultStep(anchor) + 1, operations, summaryLines, results);
        }

        Matcher beforeMatcher = ADD_BEFORE_PATTERN.matcher(clause);
        if (beforeMatcher.find()) {
            ChatReqDTO.ChatRouteNode anchor = resolveNodeByMention(beforeMatcher.group(1), currentNodes(req));
            if (anchor == null) {
                return AddOperationResult.SKIPPED;
            }
            return appendResolvedPoi(req, cleanupKeyword(beforeMatcher.group(3)), anchor.getDayNo(), defaultStep(anchor), operations, summaryLines, results);
        }

        Matcher dayMatcher = ADD_DAY_PATTERN.matcher(clause);
        if (dayMatcher.find()) {
            Integer dayNo = parseDayNo(dayMatcher.group(1));
            Integer targetIndex = StringUtils.hasText(dayMatcher.group(2)) ? Integer.parseInt(dayMatcher.group(2)) : 1;
            if (dayNo == null) {
                return AddOperationResult.SKIPPED;
            }
            return appendResolvedPoi(req, cleanupKeyword(dayMatcher.group(3)), dayNo, targetIndex, operations, summaryLines, results);
        }

        Matcher routeMatcher = ADD_TO_ROUTE_PATTERN.matcher(clause);
        if (routeMatcher.find()) {
            ChatReqDTO.ChatRouteNode anchor = lastNode(currentNodes(req));
            Integer dayNo = anchor == null ? 1 : anchor.getDayNo();
            Integer targetIndex = anchor == null ? 1 : defaultStep(anchor) + 1;
            return appendResolvedPoi(req, cleanupKeyword(routeMatcher.group(1)), dayNo, targetIndex, operations, summaryLines, results);
        }

        if (isAddIntent(clause) && StringUtils.hasText(frontKeyword)) {
            ChatReqDTO.ChatRouteNode anchor = lastNode(currentNodes(req));
            Integer dayNo = anchor == null ? 1 : anchor.getDayNo();
            Integer targetIndex = anchor == null ? 1 : defaultStep(anchor) + 1;
            return appendResolvedPoi(req, frontKeyword, dayNo, targetIndex, operations, summaryLines, results);
        }

        return AddOperationResult.SKIPPED;
    }

    private AddOperationResult appendResolvedPoi(ChatReqDTO req,
                                                 String keyword,
                                                 Integer dayNo,
                                                 Integer targetIndex,
                                                 List<ItineraryEditOperationDTO> operations,
                                                 List<String> summaryLines,
                                                 List<ChatSkillPayloadVO.ResultItem> results) {
        if (!StringUtils.hasText(keyword)) {
            return AddOperationResult.SKIPPED;
        }
        List<PoiSearchResultVO> candidates = poiService.searchLive(keyword, cityNameOf(req), 5);
        if (candidates == null || candidates.isEmpty()) {
            return AddOperationResult.CLARIFY;
        }
        PoiSearchResultVO selected = candidates.get(0);
        ItineraryEditOperationDTO operation = new ItineraryEditOperationDTO();
        operation.setType("insert_inline_custom_poi");
        operation.setDayNo(dayNo == null ? 1 : dayNo);
        operation.setTargetIndex(targetIndex == null ? 1 : targetIndex);
        operation.setStayDuration(90);
        ItineraryEditOperationDTO.CustomPoiDraft customPoiDraft = new ItineraryEditOperationDTO.CustomPoiDraft();
        customPoiDraft.setName(selected.name());
        customPoiDraft.setRoughLocation(StringUtils.hasText(selected.address()) ? selected.address() : selected.name());
        customPoiDraft.setCategory(selected.category());
        customPoiDraft.setReason("聊天新增地点");
        operation.setCustomPoiDraft(customPoiDraft);
        operations.add(operation);
        summaryLines.add("在第 " + operation.getDayNo() + " 天第 " + operation.getTargetIndex() + " 站插入「" + selected.name() + "」");
        results.add(toResultItem(selected));
        return AddOperationResult.ADDED;
    }

    private ItineraryEditOperationDTO buildStayOperation(ChatReqDTO.ChatRouteNode node, int stayDuration) {
        ItineraryEditOperationDTO operation = new ItineraryEditOperationDTO();
        operation.setType("update_stay");
        operation.setNodeKey(node.getNodeKey().trim());
        operation.setStayDuration(stayDuration);
        return operation;
    }

    private ChatSkillPayloadVO buildClarificationPayload(ChatReqDTO req,
                                                         String message,
                                                         List<ChatReqDTO.ChatRouteNode> nodes) {
        ChatSkillPayloadVO payload = basePayload();
        payload.setStatus("clarification_required");
        payload.setWorkflowState("clarification_required");
        payload.setFallbackMessage(message);
        payload.setClarificationOptions(safeNodes(nodes).stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getPoiName()))
                .map(node -> {
                    ChatSkillPayloadVO.ClarificationOption option = new ChatSkillPayloadVO.ClarificationOption();
                    option.setKey(node.getNodeKey());
                    option.setLabel(node.getPoiName().trim());
                    option.setValue(node.getPoiName().trim());
                    return option;
                })
                .toList());
        return payload;
    }

    private ChatSkillPayloadVO basePayload() {
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName(WORKFLOW_TYPE);
        payload.setMessageType(MESSAGE_TYPE);
        payload.setWorkflowType(WORKFLOW_TYPE);
        payload.setSource("local-workflow");
        return payload;
    }

    private ChatSkillPayloadVO.ActionItem action(String key, String label, String style) {
        ChatSkillPayloadVO.ActionItem action = new ChatSkillPayloadVO.ActionItem();
        action.setKey(key);
        action.setLabel(label);
        action.setStyle(style);
        return action;
    }

    private ChatSkillPayloadVO.ResultItem toResultItem(PoiSearchResultVO selected) {
        ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
        item.setName(selected.name());
        item.setAddress(selected.address());
        item.setCategory(selected.category());
        item.setLatitude(selected.latitude());
        item.setLongitude(selected.longitude());
        item.setCityName(selected.cityName());
        item.setSource(selected.source());
        return item;
    }

    private String cityNameOf(ChatReqDTO req) {
        return req != null && req.getContext() != null ? req.getContext().getCityName() : null;
    }

    private boolean hasItineraryContext(ChatReqDTO req) {
        return !currentNodes(req).isEmpty();
    }

    private List<ChatReqDTO.ChatRouteNode> currentNodes(ChatReqDTO req) {
        if (req == null || req.getContext() == null || req.getContext().getItinerary() == null || req.getContext().getItinerary().getNodes() == null) {
            return List.of();
        }
        return req.getContext().getItinerary().getNodes();
    }

    private List<ChatReqDTO.ChatRouteNode> safeNodes(List<ChatReqDTO.ChatRouteNode> nodes) {
        return nodes == null ? List.of() : nodes;
    }

    private List<String> splitClauses(String question) {
        return Arrays.stream(question.split("[，。；,;！!？?]|然后|并且|以及"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalizeQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return question;
        }
        return question.trim()
                .replaceAll("(?<=[第\\d一二三四五六七八九十天])([0-2]?\\d)点半", "$1:30")
                .replaceAll("(?<=[第\\d一二三四五六七八九十天])([0-2]?\\d)点", "$1:00")
                .replaceAll("\\s+", "");
    }

    private ChatReqDTO.ChatRouteNode resolveNodeByMention(String text, List<ChatReqDTO.ChatRouteNode> nodes) {
        return safeNodes(nodes).stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getPoiName()) && StringUtils.hasText(text))
                .filter(node -> text.contains(node.getPoiName().trim()) || node.getPoiName().trim().contains(text.trim()))
                .max(Comparator.comparingInt(node -> node.getPoiName().trim().length()))
                .orElse(null);
    }

    private ChatReqDTO.ChatRouteNode resolveAnchorWithMarker(String text,
                                                             List<ChatReqDTO.ChatRouteNode> nodes,
                                                             boolean after) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        List<String> markers = after ? List.of("后面", "后边", "之后", "后") : List.of("前面", "前边", "之前", "前");
        return safeNodes(nodes).stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getPoiName()))
                .filter(node -> markers.stream().anyMatch(marker -> text.contains(node.getPoiName().trim() + marker)))
                .max(Comparator.comparingInt(node -> node.getPoiName().trim().length()))
                .orElse(null);
    }

    private ChatReqDTO.ChatRouteNode resolveOrdinalNode(String text, List<ChatReqDTO.ChatRouteNode> nodes) {
        List<ChatReqDTO.ChatRouteNode> safe = safeNodes(nodes);
        if (safe.isEmpty() || !StringUtils.hasText(text)) {
            return null;
        }
        if (containsAny(text, "最后一站", "最后1站", "末尾")) {
            return safe.get(safe.size() - 1);
        }
        if (containsAny(text, "第一站", "第1站", "开头", "起点")) {
            return safe.get(0);
        }
        Matcher matcher = Pattern.compile("第(\\d+)站").matcher(text);
        if (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index >= 0 && index < safe.size()) {
                return safe.get(index);
            }
        }
        Matcher chineseMatcher = Pattern.compile("第([一二三四五六七八九十])站").matcher(text);
        if (chineseMatcher.find()) {
            Integer ordinal = parseDayNo(chineseMatcher.group(1));
            if (ordinal != null) {
                int index = ordinal - 1;
                if (index >= 0 && index < safe.size()) {
                    return safe.get(index);
                }
            }
        }
        return null;
    }

    private String extractFrontAddKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = FRONT_ADD_KEYWORD_PATTERN.matcher(text);
        if (matcher.find()) {
            return cleanupKeyword(matcher.group(1));
        }
        Matcher visitMatcher = VISIT_INTENT_PATTERN.matcher(text);
        if (visitMatcher.find()) {
            return cleanupKeyword(visitMatcher.group(1));
        }
        Matcher keywordAfterMatcher = KEYWORD_TO_AFTER_PATTERN.matcher(text);
        if (keywordAfterMatcher.find()) {
            return cleanupKeyword(keywordAfterMatcher.group(1));
        }
        Matcher keywordBeforeMatcher = KEYWORD_TO_BEFORE_PATTERN.matcher(text);
        if (keywordBeforeMatcher.find()) {
            return cleanupKeyword(keywordBeforeMatcher.group(1));
        }
        return "";
    }

    private boolean isAddIntent(String text) {
        return containsAny(text,
                "加入", "加上", "加到", "加在", "新增", "添加", "插入", "补上", "安排", "放入", "放到",
                "多一站", "增加一站", "想去", "要去", "想逛", "想看", "打卡", "顺便去", "带上", "带我去",
                "一定要去", "必须去");
    }

    private boolean appendStopCountClarification(ChatReqDTO req,
                                                 String question,
                                                 List<ItineraryEditOperationDTO> operations) {
        if (!operations.isEmpty()) {
            return false;
        }
        Matcher matcher = STOP_COUNT_PATTERN.matcher(question);
        if (!matcher.find()) {
            return false;
        }
        int desired = Integer.parseInt(matcher.group(1));
        int current = currentNodes(req).size();
        return desired != current && !StringUtils.hasText(extractFrontAddKeyword(question));
    }

    private String buildStopCountClarification(String question, List<ChatReqDTO.ChatRouteNode> nodes) {
        Matcher matcher = STOP_COUNT_PATTERN.matcher(question);
        int desired = matcher.find() ? Integer.parseInt(matcher.group(1)) : safeNodes(nodes).size();
        int current = safeNodes(nodes).size();
        if (desired > current) {
            return "我明白你想把路线从 " + current + " 站扩展到 " + desired + " 站。请直接告诉我要加哪个地点，例如“加入 IFS，放在建设路小吃街后面”。";
        }
        return "我明白你想把路线从 " + current + " 站缩减到 " + desired + " 站。请告诉我要删掉哪一站，例如“删掉最后一站”或“去掉建设路小吃街”。";
    }

    private Integer parseDayNo(String rawDay) {
        if (!StringUtils.hasText(rawDay)) {
            return null;
        }
        String value = rawDay.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(value);
        }
        return switch (value) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> null;
        };
    }

    private int parseDurationMinutes(String rawDuration) {
        if (!StringUtils.hasText(rawDuration)) {
            return 0;
        }
        String value = rawDuration.trim().toLowerCase(Locale.ROOT);
        if ("半小时".equals(value)) {
            return 30;
        }
        if (value.endsWith("小时半")) {
            return Integer.parseInt(value.replace("小时半", "")) * 60 + 30;
        }
        if (value.endsWith("小时")) {
            return Integer.parseInt(value.replace("小时", "")) * 60;
        }
        if (value.endsWith("分钟")) {
            return Integer.parseInt(value.replace("分钟", ""));
        }
        return 0;
    }

    private int defaultStay(ChatReqDTO.ChatRouteNode node) {
        return node == null || node.getStayDuration() == null ? 60 : Math.max(node.getStayDuration(), 0);
    }

    private int defaultStep(ChatReqDTO.ChatRouteNode node) {
        return node == null || node.getStepOrder() == null ? 1 : Math.max(node.getStepOrder(), 1);
    }

    private ChatReqDTO.ChatRouteNode lastNode(List<ChatReqDTO.ChatRouteNode> nodes) {
        List<ChatReqDTO.ChatRouteNode> safe = safeNodes(nodes);
        return safe.isEmpty() ? null : safe.get(safe.size() - 1);
    }

    private boolean containsAny(String text, String... markers) {
        if (!StringUtils.hasText(text) || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String cleanupKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return keyword
                .replace("一个", "")
                .replace("一下", "")
                .replace("地点", "")
                .replace("站点", "")
                .replace("地方", "")
                .replace("就行了", "")
                .replace("就好", "")
                .replace("即可", "")
                .replace("吧", "")
                .replace("呀", "")
                .replace("呢", "")
                .trim();
    }

    private enum AddOperationResult {
        ADDED,
        CLARIFY,
        SKIPPED
    }

    private record DraftBuildResult(boolean success,
                                    ChatSkillPayloadVO.ItineraryEditDraft draft,
                                    String message,
                                    List<ChatSkillPayloadVO.ResultItem> results) {
        private static DraftBuildResult success(ChatSkillPayloadVO.ItineraryEditDraft draft,
                                                String message,
                                                List<ChatSkillPayloadVO.ResultItem> results) {
            return new DraftBuildResult(true, draft, message, results == null ? List.of() : results);
        }

        private static DraftBuildResult clarify(String message) {
            return new DraftBuildResult(false, null, message, List.of());
        }
    }
}
