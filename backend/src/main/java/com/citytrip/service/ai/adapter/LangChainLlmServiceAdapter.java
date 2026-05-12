package com.citytrip.service.ai.adapter;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
import com.citytrip.service.ai.intent.PoiIntentCatalog;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.RetrievalDocument;
import com.citytrip.service.impl.RealLlmGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LangChainLlmServiceAdapter implements LlmService {
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*\\u6708\\s*(\\d{1,2})\\s*[\\u65e5\\u53f7]?");
    private static final Pattern COLON_TIME_PATTERN = Pattern.compile("([01]?\\d|2[0-3])[:\\uff1a]([0-5]\\d)");
    private static final Pattern HOUR_TIME_PATTERN = Pattern.compile("(\\u4e0a\\u5348|\\u65e9\\u4e0a|\\u4e2d\\u5348|\\u4e0b\\u5348|\\u665a\\u4e0a)?\\s*([0-2]?\\d)\\s*\\u70b9\\s*(?:([0-5]\\d)\\s*\\u5206?)?");
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(?:\\u9884\\u7b97|\\u82b1\\u8d39|\\u4eba\\u5747|\\u63a7\\u5236\\u5728|\\u4e0d\\u8d85\\u8fc7)?\\s*(\\d{2,5})\\s*(?:\\u5143|\\u5757|rmb|RMB)?");
    private static final Pattern EXACT_BUDGET_PATTERN = Pattern.compile(
            "(?i)(?:budget|rmb|预算|花费|人均|每人|控制|不超过|以内)\\D{0,8}(\\d{2,5})"
                    + "|(\\d{2,5})\\s*(?:rmb|RMB|元|块)\\s*(?:以内|左右|预算)?"
    );
    private static final List<String> COMMON_CITY_NAMES = List.of(
            "成都", "杭州", "北京", "上海", "广州", "深圳", "南京", "苏州", "重庆", "天津",
            "西安", "武汉", "长沙", "厦门", "青岛", "宁波", "无锡", "合肥", "郑州", "昆明",
            "大理", "丽江", "桂林", "福州", "泉州", "洛阳", "开封", "哈尔滨", "沈阳", "大连"
    );

    private final LangChainAiOrchestrator orchestrator;
    private final AiRetrieverFacade retrieverFacade;
    private final RealLlmGatewayService realLlmService;

    public LangChainLlmServiceAdapter(LangChainAiOrchestrator orchestrator) {
        this(orchestrator, null, null);
    }

    public LangChainLlmServiceAdapter(LangChainAiOrchestrator orchestrator, AiRetrieverFacade retrieverFacade) {
        this(orchestrator, retrieverFacade, null);
    }

    @Autowired
    public LangChainLlmServiceAdapter(LangChainAiOrchestrator orchestrator,
                                      AiRetrieverFacade retrieverFacade,
                                      RealLlmGatewayService realLlmService) {
        this.orchestrator = orchestrator;
        this.retrieverFacade = retrieverFacade;
        this.realLlmService = realLlmService;
    }

    @Override
    public String generateRouteWarmTip(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        orchestrator.resolveScene(AiExecutionContext.builder()
                .scene(AiScene.ROUTE_WARM_TIP)
                .userInput(buildWarmTipContext(userReq, nodes))
                .build());
        if (realLlmService != null) {
            String delegated = realLlmService.generateRouteWarmTip(userReq, nodes);
            if (StringUtils.hasText(delegated)) {
                return delegated.trim();
            }
        }

        List<String> poiNames = collectPoiNames(nodes);
        String first = poiNames.isEmpty() ? "\u8fd9\u6761\u8def\u7ebf" : poiNames.get(0);
        String second = poiNames.size() >= 2 ? poiNames.get(1) : null;
        String last = poiNames.isEmpty() ? first : poiNames.get(poiNames.size() - 1);

        String tip;
        if (Boolean.TRUE.equals(userReq == null ? null : userReq.getIsRainy())) {
            if (containsAny(first, "\u5c71", "\u5cad", "\u516c\u56ed", "\u666f\u533a")) {
                tip = shortName(first) + "\u8fd9\u6bb5\u6ce8\u610f\u9632\u6ed1\u8865\u6c34\uff0c\u96e8\u5929\u522b\u8d70\u592a\u8d76\u3002";
            } else {
                tip = "\u96e8\u5929\u8def\u6ed1\uff0c" + shortName(first) + "\u5230" + shortName(defaultIfBlank(second, last)) + "\u5c3d\u91cf\u6162\u6162\u901b\u3002";
            }
        } else if (Boolean.TRUE.equals(userReq == null ? null : userReq.getIsNight())) {
            tip = shortName(last) + "\u5c3d\u91cf\u522b\u538b\u592a\u665a\uff0c\u8fd4\u7a0b\u548c\u7b49\u8f66\u90fd\u7559\u70b9\u65f6\u95f4\u3002";
        } else if (containsAny(first, "\u718a\u732b", "\u52a8\u7269\u56ed")) {
            tip = "\u5148\u53bb" + shortName(first) + "\u4f1a\u66f4\u8f7b\u677e\uff0c\u770b\u70ed\u95e8\u70b9\u4f4d\u5c3d\u91cf\u65e9\u70b9\u5230\u3002";
        } else if (containsAny(first, "\u535a\u7269\u9986", "\u7f8e\u672f\u9986")
                || containsAny(defaultIfBlank(second, ""), "\u535a\u7269\u9986", "\u7f8e\u672f\u9986")) {
            tip = shortName(first) + "\u9002\u5408\u6162\u6162\u901b\uff0c\u70ed\u95e8\u5c55\u5385\u53ef\u4ee5\u4f18\u5148\u770b\u3002";
        } else if (containsAny(first, "\u4e07\u8c61\u57ce", "\u592a\u53e4\u91cc", "IFS", "\u6625\u7199\u8def")
                || containsAny(defaultIfBlank(second, ""), "\u4e07\u8c61\u57ce", "\u592a\u53e4\u91cc", "IFS", "\u6625\u7199\u8def")) {
            tip = shortName(first) + "\u8fd9\u6bb5\u4eba\u6d41\u4f1a\u591a\uff0c\u5403\u996d\u548c\u62cd\u7167\u5c3d\u91cf\u9519\u5cf0\u3002";
        } else if (containsAny(first, "\u5c71", "\u5cad", "\u53e4\u9547", "\u666f\u533a")) {
            tip = shortName(first) + "\u6b65\u884c\u4f1a\u591a\uff0c\u8bb0\u5f97\u8865\u6c34\u5e76\u7ed9\u8fd4\u7a0b\u7559\u4f53\u529b\u3002";
        } else if (StringUtils.hasText(second)) {
            tip = "\u4eca\u5929\u5148\u8d70" + shortName(first) + "\u5230" + shortName(second) + "\u8fd9\u6761\u7ebf\uff0c\u8fb9\u8d70\u8fb9\u4f11\u606f\u66f4\u8212\u670d\u3002";
        } else {
            tip = "\u4eca\u5929\u6309" + shortName(first) + "\u8fd9\u6761\u7ebf\u6162\u6162\u8d70\uff0c\u8282\u594f\u4f1a\u66f4\u8212\u670d\u3002";
        }
        return limitLength(tip, 40);
    }

    @Override
    public String explainOptionRecommendation(GenerateReqDTO userReq, ItineraryOptionVO option) {
        orchestrator.resolveScene(AiExecutionContext.builder()
                .scene(AiScene.OPTION_EXPLANATION)
                .userInput(buildOptionContext(userReq, option))
                .build());
        if (realLlmService != null) {
            String delegated = realLlmService.explainOptionRecommendation(userReq, option);
            if (StringUtils.hasText(delegated)) {
                return delegated.trim();
            }
        }

        List<String> poiNames = collectPoiNames(option == null ? null : option.getNodes());
        String first = poiNames.isEmpty() ? "\u8fd9\u6761\u8def\u7ebf" : shortName(poiNames.get(0));
        String second = poiNames.size() >= 2 ? shortName(poiNames.get(1)) : null;
        String preference = explainUserPreference(userReq);
        String rhythm = StringUtils.hasText(second)
                ? "\u4ece" + first + "\u5230" + second + "\u66f4\u987a\u8def"
                : first + "\u66f4\u9002\u5408\u4f5c\u4e3a\u4e3b\u7ebf";

        StringBuilder sb = new StringBuilder();
        sb.append("\u8fd9\u6761\u65b9\u6848\u4ee5").append(first);
        if (StringUtils.hasText(second)) {
            sb.append("\u3001").append(second);
        }
        sb.append("\u4e3a\u4e3b\uff0c").append(rhythm).append("\uff1b");
        if (StringUtils.hasText(preference)) {
            sb.append("\u4e5f\u66f4\u8d34\u5408").append(preference).append("\u3002");
        } else {
            sb.append("\u6574\u4f53\u8282\u594f\u4f1a\u66f4\u8f7b\u677e\u4e00\u4e9b\u3002");
        }
        String withEvidence = appendRagEvidence(sb.toString(), AiScene.OPTION_EXPLANATION, buildOptionContext(userReq, option), 110);
        return normalizeSentence(withEvidence, 110);
    }

    @Override
    public String explainPoiChoice(GenerateReqDTO userReq, ItineraryNodeVO node) {
        orchestrator.resolveScene(AiExecutionContext.builder()
                .scene(AiScene.POI_EXPLANATION)
                .userInput(buildPoiContext(userReq, node))
                .build());
        if (realLlmService != null) {
            String delegated = realLlmService.explainPoiChoice(userReq, node);
            if (StringUtils.hasText(delegated)) {
                return delegated.trim();
            }
        }

        if (node == null) {
            return "\u8fd9\u4e2a\u70b9\u4f4d\u548c\u5f53\u524d\u8def\u7ebf\u66f4\u987a\u8def\uff0c\u9002\u5408\u4f5c\u4e3a\u8fd9\u6bb5\u884c\u7a0b\u7684\u4e00\u7ad9\u3002";
        }
        String poiName = shortName(node.getPoiName());
        String category = defaultIfBlank(node.getCategory(), "\u8fd9\u4e2a\u70b9\u4f4d");
        String district = defaultIfBlank(node.getDistrict(), "");
        String reason = defaultIfBlank(node.getSysReason(), "");
        String preference = explainUserPreference(userReq);
        String scoreFocus = summarizeScoreBreakdown(node.getScoreBreakdown());

        StringBuilder sb = new StringBuilder();
        sb.append(poiName).append("\u5c5e\u4e8e").append(category);
        if (StringUtils.hasText(district)) {
            sb.append("\uff0c\u5728").append(district);
        }
        sb.append("\uff1b");
        if (StringUtils.hasText(reason)) {
            sb.append(reason);
            if (!reason.endsWith("\u3002") && !reason.endsWith("\uff1b")) {
                sb.append("\u3002");
            }
        }
        if (StringUtils.hasText(scoreFocus)) {
            sb.append(scoreFocus).append("\uff1b");
        }
        if (StringUtils.hasText(preference)) {
            sb.append("\u4e5f\u66f4\u8d34\u5408").append(preference).append("\u3002");
        } else {
            sb.append("\u653e\u5728\u8fd9\u6bb5\u884c\u7a0b\u91cc\u4f1a\u66f4\u987a\u8def\u3002");
        }
        String withEvidence = appendRagEvidence(sb.toString(), AiScene.POI_EXPLANATION, buildPoiContext(userReq, node), 130);
        return normalizeSentence(withEvidence, 130);
    }

    @Override
    public SmartFillVO parseSmartFill(String text, List<String> poiNameHints) {
        String normalizedText = StringUtils.hasText(text) ? text.trim() : "";
        orchestrator.resolveScene(AiExecutionContext.builder()
                .scene(AiScene.SMART_FILL)
                .userInput(normalizedText)
                .build());
        if (realLlmService != null) {
            SmartFillVO delegated = realLlmService.parseSmartFill(text, poiNameHints);
            if (delegated != null) {
                return mergeDeterministicSmartFillHints(delegated, normalizedText, poiNameHints);
            }
        }
        return buildDeterministicSmartFill(normalizedText, poiNameHints);
    }

    private SmartFillVO buildDeterministicSmartFill(String normalizedText, List<String> poiNameHints) {
        SmartFillVO vo = new SmartFillVO();
        String cityName = resolveCityName(normalizedText, poiNameHints);
        vo.setCityName(cityName);
        vo.setTripDate(parseTripDate(normalizedText));
        vo.setStartTime(parseStartTime(normalizedText));
        vo.setTripDays(parseTripDays(normalizedText));
        vo.setBudgetLevel(parseBudgetLevel(normalizedText));
        vo.setTotalBudget(parseTotalBudget(normalizedText));
        vo.setWalkingLevel(parseWalkingLevel(normalizedText));
        vo.setThemes(parseThemes(normalizedText));
        vo.setMustVisitPoiNames(resolveMustVisitPoiNames(normalizedText, poiNameHints, cityName));
        vo.setPreferredPoiCategories(parsePreferredPoiCategories(normalizedText));
        vo.setExcludedPoiCategories(parseExcludedPoiCategories(normalizedText));
        vo.setConflictWarnings(parseConflictWarnings(normalizedText));
        vo.setAlternativePoiHints(parseAlternativePoiHints(normalizedText, poiNameHints, cityName));
        vo.setBudgetTight(resolveBudgetTight(normalizedText, vo.getBudgetLevel(), vo.getTotalBudget()));
        vo.setSummary(buildSmartFillSummary(vo));
        return vo;
    }

    private SmartFillVO mergeDeterministicSmartFillHints(SmartFillVO delegated,
                                                        String normalizedText,
                                                        List<String> poiNameHints) {
        SmartFillVO deterministic = buildDeterministicSmartFill(normalizedText, poiNameHints);
        String explicitCity = findCommonCityName(normalizedText);
        if (StringUtils.hasText(explicitCity)) {
            delegated.setCityName(explicitCity);
        } else if (!StringUtils.hasText(delegated.getCityName())) {
            delegated.setCityName(deterministic.getCityName());
        }
        if (deterministic.getTotalBudget() != null) {
            delegated.setTotalBudget(deterministic.getTotalBudget());
            delegated.setBudgetTight(true);
            String parsedBudgetLevel = parseBudgetLevel(normalizedText);
            if (StringUtils.hasText(parsedBudgetLevel)) {
                delegated.setBudgetLevel(parsedBudgetLevel);
            }
        } else if (delegated.getBudgetTight() == null) {
            delegated.setBudgetTight(deterministic.getBudgetTight());
        }
        if (!StringUtils.hasText(delegated.getBudgetLevel())) {
            delegated.setBudgetLevel(deterministic.getBudgetLevel());
        }
        if (delegated.getMustVisitPoiNames() == null || delegated.getMustVisitPoiNames().isEmpty()) {
            delegated.setMustVisitPoiNames(deterministic.getMustVisitPoiNames());
        }
        delegated.setThemes(mergeTextLists(
                delegated.getThemes(),
                deterministic.getThemes()
        ));
        delegated.setPreferredPoiCategories(mergeTextLists(
                delegated.getPreferredPoiCategories(),
                deterministic.getPreferredPoiCategories()
        ));
        delegated.setExcludedPoiCategories(mergeTextLists(
                delegated.getExcludedPoiCategories(),
                deterministic.getExcludedPoiCategories()
        ));
        delegated.setConflictWarnings(mergeTextLists(
                delegated.getConflictWarnings(),
                deterministic.getConflictWarnings()
        ));
        delegated.setAlternativePoiHints(mergeTextLists(
                delegated.getAlternativePoiHints(),
                deterministic.getAlternativePoiHints()
        ));
        delegated.setSummary(mergeSmartFillSummary(delegated.getSummary(), buildSmartFillSummary(delegated)));
        return delegated;
    }

    @Override
    public DepartureLegEstimateVO estimateDepartureLeg(GenerateReqDTO userReq, ItineraryNodeVO firstNode) {
        if (realLlmService != null) {
            DepartureLegEstimateVO delegated = realLlmService.estimateDepartureLeg(userReq, firstNode);
            if (delegated != null) {
                return delegated;
            }
        }
        return null;
    }

    @Override
    public SegmentTransportAnalysisVO analyzeSegmentTransport(GenerateReqDTO userReq, ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        if (realLlmService != null) {
            SegmentTransportAnalysisVO delegated = realLlmService.analyzeSegmentTransport(userReq, fromNode, toNode);
            if (delegated != null) {
                return delegated;
            }
        }
        return null;
    }

    @Override
    public RouteCriticDecisionVO criticSelectItineraryOption(GenerateReqDTO userReq, List<ItineraryOptionVO> options) {
        if (realLlmService != null) {
            return realLlmService.criticSelectItineraryOption(userReq, options);
        }
        return LlmService.super.criticSelectItineraryOption(userReq, options);
    }

    @Override
    public ItineraryRouteDecorationVO decorateRouteExperience(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        if (realLlmService != null) {
            return realLlmService.decorateRouteExperience(userReq, nodes);
        }
        return LlmService.super.decorateRouteExperience(userReq, nodes);
    }

    private String resolveCityName(String text, List<String> poiNameHints) {
        String cityFromText = findCommonCityName(text);
        if (StringUtils.hasText(cityFromText)) {
            return cityFromText;
        }
        if (poiNameHints != null) {
            for (String poiName : poiNameHints) {
                String cityFromPoi = findCommonCityName(poiName);
                if (StringUtils.hasText(cityFromPoi)) {
                    return cityFromPoi;
                }
            }
        }
        return "\u6210\u90fd";
    }

    private String findCommonCityName(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        for (String city : COMMON_CITY_NAMES) {
            if (text.contains(city + "市") || text.contains(city)) {
                return city;
            }
        }
        return null;
    }

    private String parseTripDate(String text) {
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(text);
        if (isoMatcher.find()) {
            String[] parts = isoMatcher.group(1).split("-");
            return "%04d-%02d-%02d".formatted(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        Matcher monthDayMatcher = MONTH_DAY_PATTERN.matcher(text);
        if (monthDayMatcher.find()) {
            int year = LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear();
            return "%04d-%02d-%02d".formatted(year, Integer.parseInt(monthDayMatcher.group(1)), Integer.parseInt(monthDayMatcher.group(2)));
        }
        return null;
    }

    private String parseStartTime(String text) {
        Matcher colonMatcher = COLON_TIME_PATTERN.matcher(text);
        if (colonMatcher.find()) {
            return "%02d:%02d".formatted(Integer.parseInt(colonMatcher.group(1)), Integer.parseInt(colonMatcher.group(2)));
        }
        Matcher hourMatcher = HOUR_TIME_PATTERN.matcher(text);
        if (hourMatcher.find()) {
            int hour = Integer.parseInt(hourMatcher.group(2));
            int minute = StringUtils.hasText(hourMatcher.group(3)) ? Integer.parseInt(hourMatcher.group(3)) : 0;
            String period = hourMatcher.group(1);
            if (StringUtils.hasText(period)) {
                if ((period.contains("\u4e0b\u5348") || period.contains("\u665a\u4e0a")) && hour < 12) {
                    hour += 12;
                }
                if (period.contains("\u4e2d\u5348") && hour < 11) {
                    hour += 12;
                }
            }
            if (hour >= 0 && hour <= 23) {
                return "%02d:%02d".formatted(hour, minute);
            }
        }
        return null;
    }

    private Double parseTripDays(String text) {
        if (text.contains("\u534a\u5929") || text.contains("\u534a\u65e5")) {
            return 0.5D;
        }
        if (text.contains("\u4e24\u5929") || text.contains("\u4e8c\u5929") || text.contains("2\u5929")) {
            return 2.0D;
        }
        if (text.contains("\u4e00\u5929") || text.contains("1\u5929") || text.contains("\u5168\u5929")) {
            return 1.0D;
        }
        return null;
    }

    private String parseBudgetLevel(String text) {
        if (text.contains("\u7701\u94b1") || text.contains("\u4f4e\u9884\u7b97")) {
            return "\u4f4e";
        }
        if (text.contains("\u4e0d\u5dee\u94b1") || text.contains("\u9ad8\u9884\u7b97")) {
            return "\u9ad8";
        }
        Matcher matcher = BUDGET_PATTERN.matcher(text);
        while (matcher.find()) {
            int budget = Integer.parseInt(matcher.group(1));
            if (budget < 30) {
                continue;
            }
            if (budget <= 100) {
                return "\u4f4e";
            }
            if (budget <= 300) {
                return "\u4e2d";
            }
            return "\u9ad8";
        }
        return null;
    }

    private Double parseTotalBudget(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = EXACT_BUDGET_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                double value = Double.parseDouble(raw);
                if (value >= 30D && value <= 1000000D) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed budget fragments.
            }
        }
        return null;
    }

    private String parseWalkingLevel(String text) {
        if (containsAny(text, "\u4e0d\u60f3\u592a\u7d2f", "\u522b\u592a\u7d2f", "\u5c11\u8d70", "\u8f7b\u677e", "\u4e0d\u7d2f")) {
            return "\u4f4e";
        }
        if (containsAny(text, "\u66b4\u8d70", "\u722c\u5c71", "\u591a\u8d70", "\u80fd\u8d70")) {
            return "\u9ad8";
        }
        return null;
    }

    private List<String> parseThemes(String text) {
        Set<String> themes = new LinkedHashSet<>();
        themes.addAll(PoiIntentCatalog.themes(text));
        if (containsAny(text, "\u6587\u5316", "\u5386\u53f2", "\u535a\u7269\u9986", "\u53e4\u9547")) {
            themes.add("\u6587\u5316");
        }
        if (containsAny(text, "\u8d2d\u7269", "\u5546\u573a", "\u901b\u8857", "\u4e07\u8c61\u57ce", "IFS", "\u592a\u53e4\u91cc")) {
            themes.add("\u8d2d\u7269");
        }
        if (containsAny(text, "\u7f8e\u98df", "\u5c0f\u5403", "\u591c\u5e02", "\u5403\u996d")) {
            themes.add("\u7f8e\u98df");
        }
        if (containsAny(text, "\u516c\u56ed", "\u81ea\u7136", "\u52a8\u7269\u56ed", "\u718a\u732b", "\u9752\u57ce\u5c71", "\u90fd\u6c5f\u5830")) {
            themes.add("\u81ea\u7136");
        }
        if (containsAny(text, "\u62cd\u7167", "\u6253\u5361", "\u7f51\u7ea2")) {
            themes.add("\u7f51\u7ea2");
        }
        if (containsAny(text, "\u4e0d\u60f3\u592a\u7d2f", "\u8f7b\u677e", "\u4f11\u95f2", "\u6162\u6162\u901b")) {
            themes.add("\u4f11\u95f2");
        }
        return new ArrayList<>(themes);
    }

    private List<String> parsePreferredPoiCategories(String text) {
        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(PoiIntentCatalog.preferredCategories(text));
        if (containsAny(text, "猴子", "熊猫", "动物", "看动物")) {
            categories.add("动物园");
            categories.add("景区");
        }
        return new ArrayList<>(categories);
    }

    private List<String> parseExcludedPoiCategories(String text) {
        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(PoiIntentCatalog.excludedCategories(text));
        if (containsAny(text, "猴子", "熊猫", "动物", "看动物") && containsAny(text, "万象城", "商场", "购物")) {
            categories.add("商场");
        }
        return new ArrayList<>(categories);
    }

    private List<String> parseConflictWarnings(String text) {
        List<String> warnings = new ArrayList<>();
        if (containsAny(text, "猴子", "熊猫", "动物", "看动物") && containsAny(text, "万象城", "商场", "购物")) {
            warnings.add("万象城不适合看猴子，已为你改用更匹配的动物类点位");
        }
        if (containsAny(text, "火锅") && containsAny(text, "博物馆")) {
            warnings.add("吃火锅属于餐饮诉求，博物馆类点位会被降权处理");
        }
        return warnings;
    }

    private List<String> parseAlternativePoiHints(String text, List<String> poiNameHints, String cityName) {
        Set<String> alternatives = new LinkedHashSet<>();
        alternatives.addAll(resolveCanonicalHints(PoiIntentCatalog.alternativeHints(text), poiNameHints, cityName));
        if (containsAny(text, "猴子", "熊猫", "动物", "看动物")) {
            alternatives.addAll(resolveCanonicalHints(List.of("动物园", "熊猫基地"), poiNameHints, cityName));
        }
        return new ArrayList<>(alternatives);
    }

    private List<String> resolveCanonicalHints(List<String> rawHints, List<String> poiNameHints, String cityName) {
        Set<String> resolved = new LinkedHashSet<>();
        if (rawHints == null) {
            return List.of();
        }
        List<String> availableHints = poiNameHints == null ? List.of() : poiNameHints;
        for (String rawHint : rawHints) {
            if (!StringUtils.hasText(rawHint)) {
                continue;
            }
            String normalized = normalizeComparableText(rawHint);
            for (String poiName : availableHints) {
                if (StringUtils.hasText(poiName) && normalizeComparableText(poiName).contains(normalized)) {
                    resolved.add(poiName.trim());
                }
            }
            if (containsAny(rawHint, "熊猫", "熊貓", "panda")) {
                for (String poiName : availableHints) {
                    if (!StringUtils.hasText(poiName)) {
                        continue;
                    }
                    String comparablePoiName = normalizeComparableText(poiName);
                    if (comparablePoiName.contains("熊猫") || comparablePoiName.contains("熊貓")
                            || comparablePoiName.contains("panda") || comparablePoiName.contains("繁育研究基地")) {
                        resolved.add(poiName.trim());
                    }
                }
            }
            if (resolved.stream().noneMatch(item -> normalizeComparableText(item).contains(normalized))) {
                List<String> mustVisit = resolveMustVisitPoiNames(rawHint, poiNameHints, cityName);
                if (mustVisit != null) {
                    resolved.addAll(mustVisit);
                }
            }
        }
        return new ArrayList<>(resolved);
    }

    private Boolean resolveBudgetTight(String text, String budgetLevel, Double totalBudget) {
        if (totalBudget != null) {
            return true;
        }
        if (containsAny(text, "预算", "省钱", "控制在", "不超过")) {
            return !"高".equals(budgetLevel);
        }
        return "低".equals(budgetLevel);
    }

    private List<String> resolveMustVisitPoiNames(String text, List<String> poiNameHints, String cityName) {
        if (poiNameHints == null || poiNameHints.isEmpty() || !StringUtils.hasText(text)) {
            return List.of();
        }
        Map<String, String> aliasIndex = new LinkedHashMap<>();
        for (String poiName : poiNameHints) {
            if (!StringUtils.hasText(poiName)) {
                continue;
            }
            String canonical = poiName.trim();
            registerAlias(aliasIndex, canonical, canonical);
            if (canonical.startsWith(cityName) && canonical.length() > cityName.length()) {
                registerAlias(aliasIndex, canonical.substring(cityName.length()).trim(), canonical);
            }
            if (canonical.startsWith(cityName + "\u5e02") && canonical.length() > cityName.length() + 1) {
                registerAlias(aliasIndex, canonical.substring(cityName.length() + 1).trim(), canonical);
            }
        }
        Set<String> resolved = new LinkedHashSet<>();
        String normalizedText = normalizeComparableText(text);
        aliasIndex.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .forEach(entry -> {
                    if (normalizedText.contains(entry.getKey())) {
                        resolved.add(entry.getValue());
                    }
                });
        return new ArrayList<>(resolved);
    }

    private void registerAlias(Map<String, String> aliasIndex, String alias, String canonical) {
        String normalized = normalizeComparableText(alias);
        if (StringUtils.hasText(normalized) && normalized.length() >= 2) {
            aliasIndex.putIfAbsent(normalized, canonical);
        }
    }

    private String normalizeComparableText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。！？!?:：；、\"'“”‘’()（）\\[\\]{}<>《》·`~_-]+", "");
    }

    private List<String> buildSmartFillSummary(SmartFillVO vo) {
        List<String> summary = new ArrayList<>();
        if (vo.getMustVisitPoiNames() != null && !vo.getMustVisitPoiNames().isEmpty()) {
            summary.add("\u5fc5\u53bb\uff1a" + String.join("\u3001", vo.getMustVisitPoiNames()));
        }
        if (vo.getThemes() != null && !vo.getThemes().isEmpty()) {
            summary.add("\u4e3b\u9898\uff1a" + String.join("\u3001", vo.getThemes()));
        }
        if (StringUtils.hasText(vo.getTripDate())) {
            summary.add("\u51fa\u884c\u65e5\uff1a" + vo.getTripDate());
        }
        if (StringUtils.hasText(vo.getStartTime())) {
            summary.add("\u51fa\u53d1\u65f6\u95f4\uff1a" + vo.getStartTime());
        }
        if (vo.getTotalBudget() != null) {
            summary.add("\u9884\u7b97\uff1a" + Math.round(vo.getTotalBudget()) + "\u5143");
        } else if (StringUtils.hasText(vo.getBudgetLevel())) {
            summary.add("\u9884\u7b97\uff1a" + vo.getBudgetLevel());
        }
        if (vo.getConflictWarnings() != null) {
            summary.addAll(vo.getConflictWarnings());
        }
        if (vo.getPreferredPoiCategories() != null && !vo.getPreferredPoiCategories().isEmpty()) {
            summary.add("\u504f\u597d\uff1a" + String.join("\u3001", vo.getPreferredPoiCategories()));
        }
        return summary;
    }

    private List<String> mergeTextLists(List<String> primary, List<String> secondary) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            primary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (secondary != null) {
            secondary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private List<String> mergeSmartFillSummary(List<String> primary, List<String> secondary) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            primary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (secondary != null) {
            secondary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private String buildOptionContext(GenerateReqDTO userReq, ItineraryOptionVO option) {
        StringBuilder sb = new StringBuilder();
        if (option != null && option.getNodes() != null) {
            sb.append("\u5019\u9009\u8def\u7ebf\uff1a").append(String.join(" -> ", collectPoiNames(option.getNodes()))).append("\uff1b");
        }
        if (option != null && option.getTotalDuration() != null) {
            sb.append("\u603b\u65f6\u957f").append(option.getTotalDuration()).append("\u5206\u949f\uff1b");
        }
        if (option != null && option.getTotalCost() != null) {
            sb.append("\u603b\u82b1\u8d39").append(option.getTotalCost()).append("\u5143\uff1b");
        }
        if (StringUtils.hasText(explainUserPreference(userReq))) {
            sb.append("\u504f\u597d\uff1a").append(explainUserPreference(userReq));
        }
        return sb.toString();
    }

    private String buildPoiContext(GenerateReqDTO userReq, ItineraryNodeVO node) {
        StringBuilder sb = new StringBuilder();
        if (node != null) {
            sb.append(defaultIfBlank(node.getPoiName(), "\u672a\u77e5\u70b9\u4f4d"));
            if (StringUtils.hasText(node.getCategory())) {
                sb.append("\uff1b\u5206\u7c7b\uff1a").append(node.getCategory());
            }
            if (StringUtils.hasText(node.getDistrict())) {
                sb.append("\uff1b\u533a\u57df\uff1a").append(node.getDistrict());
            }
        }
        if (StringUtils.hasText(explainUserPreference(userReq))) {
            sb.append("\uff1b\u504f\u597d\uff1a").append(explainUserPreference(userReq));
        }
        return sb.toString();
    }

    private String appendRagEvidence(String baseText, AiScene scene, String userInput, int maxLength) {
        if (retrieverFacade == null || !StringUtils.hasText(baseText)) {
            return baseText;
        }
        List<RetrievalDocument> documents = retrieverFacade.retrieve(AiExecutionContext.builder()
                .scene(scene)
                .userInput(userInput)
                .build());
        if (documents == null || documents.isEmpty() || !StringUtils.hasText(documents.get(0).content())) {
            return baseText;
        }
        String evidence = documents.get(0).content().trim();
        if (baseText.contains(evidence)) {
            return baseText;
        }
        return limitLength(baseText + "\u53e6\u53ef\u53c2\u8003\uff1a" + evidence, maxLength);
    }

    private String explainUserPreference(GenerateReqDTO userReq) {
        if (userReq == null) {
            return null;
        }
        if (StringUtils.hasText(userReq.getWalkingLevel()) && "\u4f4e".equals(userReq.getWalkingLevel().trim())) {
            return "\u5c11\u8d70\u8def\u3001\u8f7b\u677e\u4e00\u70b9";
        }
        if (userReq.getThemes() != null && !userReq.getThemes().isEmpty()) {
            return String.join("\u3001", userReq.getThemes()) + "\u4e3b\u9898";
        }
        if (Boolean.TRUE.equals(userReq.getIsNight())) {
            return "\u591c\u6e38\u8282\u594f";
        }
        return null;
    }

    private String summarizeScoreBreakdown(Map<String, Double> scoreBreakdown) {
        if (scoreBreakdown == null || scoreBreakdown.isEmpty()) {
            return null;
        }
        return scoreBreakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.nullsLast(Double::compareTo)).reversed())
                .map(Map.Entry::getKey)
                .findFirst()
                .map(this::humanizeScoreKey)
                .map(label -> label + "\u8868\u73b0\u66f4\u597d")
                .orElse(null);
    }

    private String humanizeScoreKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "\u7efc\u5408\u5339\u914d";
        }
        return switch (key.trim()) {
            case "theme" -> "\u4e3b\u9898\u5339\u914d";
            case "walking" -> "\u6b65\u884c\u8d1f\u62c5";
            case "budget" -> "\u9884\u7b97\u9002\u914d";
            case "time" -> "\u65f6\u95f4\u5b89\u6392";
            default -> "\u7efc\u5408\u5339\u914d";
        };
    }

    private String normalizeSentence(String text, int maxLength) {
        String normalized = limitLength(text, maxLength);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        normalized = normalized.replace("\uff1b\u3002", "\u3002").replace("\u3002\u3002", "\u3002");
        return normalized;
    }

    private List<String> collectPoiNames(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (ItineraryNodeVO node : nodes) {
            if (node != null && StringUtils.hasText(node.getPoiName())) {
                names.add(node.getPoiName().trim());
            }
        }
        return new ArrayList<>(names);
    }

    private String buildWarmTipContext(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        List<String> poiNames = collectPoiNames(nodes);
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(userReq == null ? null : userReq.getIsRainy())) {
            sb.append("\u96e8\u5929\uff1b");
        }
        if (Boolean.TRUE.equals(userReq == null ? null : userReq.getIsNight())) {
            sb.append("\u591c\u6e38\uff1b");
        }
        if (!poiNames.isEmpty()) {
            sb.append("\u8def\u7ebf\uff1a").append(String.join(" -> ", poiNames));
        }
        return sb.toString();
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String shortName(String name) {
        if (!StringUtils.hasText(name)) {
            return "\u8fd9\u6bb5\u884c\u7a0b";
        }
        String value = name.trim();
        if (value.startsWith("\u6210\u90fd") && value.length() > 2) {
            value = value.substring(2);
        }
        return limitLength(value, 12);
    }

    private String defaultIfBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String limitLength(String text, int maxLength) {
        if (!StringUtils.hasText(text) || maxLength <= 0) {
            return text;
        }
        String normalized = text.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
