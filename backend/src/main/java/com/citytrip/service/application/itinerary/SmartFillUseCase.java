package com.citytrip.service.application.itinerary;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
import com.citytrip.service.geo.CityResolverService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SmartFillUseCase {

    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern COLON_TIME_PATTERN = Pattern.compile("([01]?\\d|2[0-3])[:：]([0-5]\\d)");
    private static final Pattern HOUR_TIME_PATTERN = Pattern.compile("(上午|早上|中午|下午|晚上|晚间)?\\s*([0-2]?\\d)\\s*点(?:\\s*([0-5]\\d)\\s*分?)?");
    private static final Pattern BUDGET_NUMBER_PATTERN = Pattern.compile("(?:预算|花费|人均|每人|控制在|不超过|以内|大概|大约)?\\s*(\\d{2,5})\\s*(?:元|块|rmb|RMB)?");
    private static final Pattern DEPARTURE_FROM_PATTERN = Pattern.compile("(?:从|從)([^，。,\\s]{2,24})(?:出发|開始|开始|过去|前往|到)");
    private static final Pattern DEPARTURE_STAY_PATTERN = Pattern.compile("(?:住在|住|酒店在)([^，。,\\s]{2,24})");

    private static final Set<String> ALLOWED_THEMES = Set.of("文化", "美食", "自然", "购物", "网红", "休闲");
    private static final Set<String> ALLOWED_BUDGETS = Set.of("低", "中", "高");
    private static final Set<String> ALLOWED_WALKING = Set.of("低", "中", "高");
    private static final Set<String> ALLOWED_COMPANION = Set.of("独自", "朋友", "情侣", "亲子");
    private static final int POI_HINT_LIMIT = 200;

    private static final String IFS_FALLBACK_NAME = "IFS国际金融中心";
    private static final String PANDA_BASE_FALLBACK_NAME = "成都大熊猫繁育研究基地";
    private static final String ZOO_FALLBACK_NAME = "成都动物园";
    private static final Set<String> IFS_ALIAS_SKILL = Set.of(
            "ifs",
            "国金",
            "金融中心",
            "ifs金融中心",
            "ifs国际金融中心"
    );
    private static final Set<String> PANDA_INTENT_KEYWORDS = Set.of(
            "熊猫",
            "大熊猫",
            "熊貓",
            "panda",
            "繁育基地",
            "繁衍基地",
            "熊猫基地"
    );

    private final LlmService llmService;
    private final PoiMapper poiMapper;
    private final PlaceDisambiguationService placeDisambiguationService;
    private final GeoSearchService geoSearchService;
    private final CityResolverService cityResolverService;

    @Autowired
    public SmartFillUseCase(LlmService llmService,
                            PoiMapper poiMapper,
                            PlaceDisambiguationService placeDisambiguationService,
                            GeoSearchService geoSearchService,
                            CityResolverService cityResolverService) {
        this.llmService = llmService;
        this.poiMapper = poiMapper;
        this.placeDisambiguationService = placeDisambiguationService;
        this.geoSearchService = geoSearchService;
        this.cityResolverService = cityResolverService;
    }

    SmartFillUseCase(LlmService llmService, PoiMapper poiMapper) {
        this(llmService, poiMapper, null, null, null);
    }

    public SmartFillVO parse(SmartFillReqDTO req) {
        SmartFillVO empty = new SmartFillVO();
        if (req == null || !StringUtils.hasText(req.getText())) {
            return empty;
        }
        String text = req.getText().trim();
        String cityHint = resolveCityName(null, text);
        List<String> poiHints = loadPoiNameHints(cityHint);

        SmartFillVO parsed;
        try {
            parsed = llmService.parseSmartFill(text, poiHints);
        } catch (RuntimeException ex) {
            parsed = new SmartFillVO();
        }
        if (parsed == null) {
            parsed = new SmartFillVO();
        }

        mergeMissingFields(parsed, buildLocalFallback(text, cityHint));
        sanitizeParsedResult(parsed, text, poiHints, cityHint);
        enrichGeoFields(parsed, text);
        return parsed;
    }

    private SmartFillVO buildLocalFallback(String text, String cityHint) {
        SmartFillVO fallback = new SmartFillVO();
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        String normalized = text.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        fallback.setCityName(cityHint);
        fallback.setTripDays(parseTripDays(normalized));
        fallback.setTripDate(parseTripDate(normalized));
        List<String> times = parseTimes(normalized);
        if (!times.isEmpty()) {
            fallback.setStartTime(times.get(0));
        }
        if (times.size() >= 2) {
            fallback.setEndTime(times.get(1));
        } else if (fallback.getStartTime() != null && (normalized.contains("半天") || normalized.contains("上午"))) {
            fallback.setEndTime(addHours(fallback.getStartTime(), 4));
        }
        fallback.setBudgetLevel(parseBudgetLevel(normalized));
        fallback.setThemes(parseThemes(normalized));
        fallback.setCompanionType(parseCompanion(normalized));
        fallback.setWalkingLevel(parseWalkingLevel(normalized));
        fallback.setMustVisitPoiNames(resolveIntentPoiNames(normalized, List.of()));
        if (lower.contains("雨") || lower.contains("rain")) {
            fallback.setIsRainy(true);
        }
        if (normalized.contains("夜") || normalized.contains("晚上") || normalized.contains("夜市") || lower.contains("night")) {
            fallback.setIsNight(true);
        }
        fallback.setDepartureText(extractDepartureText(normalized));
        return fallback;
    }

    private void mergeMissingFields(SmartFillVO target, SmartFillVO fallback) {
        if (target == null || fallback == null) {
            return;
        }
        if (target.getTripDays() == null) target.setTripDays(fallback.getTripDays());
        if (!StringUtils.hasText(target.getTripDate())) target.setTripDate(fallback.getTripDate());
        if (!StringUtils.hasText(target.getStartTime())) target.setStartTime(fallback.getStartTime());
        if (!StringUtils.hasText(target.getEndTime())) target.setEndTime(fallback.getEndTime());
        if (!StringUtils.hasText(target.getBudgetLevel())) target.setBudgetLevel(fallback.getBudgetLevel());
        if (target.getThemes() == null || target.getThemes().isEmpty()) target.setThemes(fallback.getThemes());
        if (target.getMustVisitPoiNames() == null || target.getMustVisitPoiNames().isEmpty()) target.setMustVisitPoiNames(fallback.getMustVisitPoiNames());
        if (!StringUtils.hasText(target.getCompanionType())) target.setCompanionType(fallback.getCompanionType());
        if (!StringUtils.hasText(target.getWalkingLevel())) target.setWalkingLevel(fallback.getWalkingLevel());
        if (target.getIsRainy() == null) target.setIsRainy(fallback.getIsRainy());
        if (target.getIsNight() == null) target.setIsNight(fallback.getIsNight());
        if (!StringUtils.hasText(target.getCityName())) target.setCityName(fallback.getCityName());
        if (!StringUtils.hasText(target.getDepartureText())) target.setDepartureText(fallback.getDepartureText());
    }

    private Double parseTripDays(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.contains("半天") || text.contains("半日") || text.contains("上午") || text.contains("下午半天")) {
            return 0.5D;
        }
        if (text.contains("两天") || text.contains("二天") || text.contains("2天") || text.contains("两日") || text.contains("二日") || text.contains("2日")) {
            return 2.0D;
        }
        if (text.contains("一天") || text.contains("一日") || text.contains("全天") || text.contains("1天") || text.contains("1日")) {
            return 1.0D;
        }
        return null;
    }

    private String parseTripDate(String text) {
        Matcher iso = ISO_DATE_PATTERN.matcher(text);
        if (iso.find()) {
            String[] parts = iso.group(1).split("-");
            return "%04d-%02d-%02d".formatted(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        Matcher md = MONTH_DAY_PATTERN.matcher(text);
        if (md.find()) {
            int year = LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear();
            return "%04d-%02d-%02d".formatted(year, Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2)));
        }
        return null;
    }

    private List<String> parseTimes(String text) {
        List<String> result = new ArrayList<>();
        Matcher colon = COLON_TIME_PATTERN.matcher(text);
        while (colon.find() && result.size() < 2) {
            result.add("%02d:%02d".formatted(Integer.parseInt(colon.group(1)), Integer.parseInt(colon.group(2))));
        }
        if (!result.isEmpty()) {
            return result;
        }
        Matcher hour = HOUR_TIME_PATTERN.matcher(text);
        while (hour.find() && result.size() < 2) {
            int rawHour = Integer.parseInt(hour.group(2));
            int minute = StringUtils.hasText(hour.group(3)) ? Integer.parseInt(hour.group(3)) : 0;
            String period = hour.group(1);
            int hour24 = normalizeHourByPeriod(rawHour, period);
            if (hour24 >= 0 && hour24 <= 23 && minute >= 0 && minute <= 59) {
                result.add("%02d:%02d".formatted(hour24, minute));
            }
        }
        return result;
    }

    private int normalizeHourByPeriod(int hour, String period) {
        if (StringUtils.hasText(period)) {
            if ((period.contains("下午") || period.contains("晚上") || period.contains("晚间")) && hour < 12) {
                return hour + 12;
            }
            if (period.contains("中午") && hour < 11) {
                return hour + 12;
            }
        }
        return hour;
    }

    private String addHours(String time, int hours) {
        String[] parts = time.split(":");
        int hour = Math.min(23, Integer.parseInt(parts[0]) + Math.max(hours, 0));
        return "%02d:%s".formatted(hour, parts[1]);
    }

    private String parseBudgetLevel(String text) {
        if (text.contains("低预算") || text.contains("省钱") || text.contains("便宜")) {
            return "低";
        }
        if (text.contains("高预算") || text.contains("不差钱") || text.contains("贵一点")) {
            return "高";
        }
        Matcher matcher = BUDGET_NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            String context = text.substring(Math.max(0, matcher.start() - 8), Math.min(text.length(), matcher.end() + 4));
            if (!context.contains("预算")
                    && !context.contains("花费")
                    && !context.contains("人均")
                    && !context.contains("每人")
                    && !context.contains("控制")
                    && !context.contains("不超过")
                    && !context.contains("以内")
                    && !context.contains("元")
                    && !context.contains("块")) {
                continue;
            }
            int value = Integer.parseInt(matcher.group(1));
            if (value < 30) {
                continue;
            }
            if (value <= 100) return "低";
            if (value <= 300) return "中";
            return "高";
        }
        return null;
    }

    private List<String> parseThemes(String text) {
        Set<String> themes = new LinkedHashSet<>();
        if (text.contains("历史") || text.contains("文化") || text.contains("博物馆") || text.contains("古镇")) themes.add("文化");
        if (text.contains("美食") || text.contains("吃") || text.contains("小吃") || text.contains("夜市")) themes.add("美食");
        if (text.contains("自然") || text.contains("公园") || text.contains("山") || text.contains("湖") || text.contains("熊猫")) themes.add("自然");
        if (text.contains("购物") || text.contains("商场") || text.contains("逛街")) themes.add("购物");
        if (text.contains("网红") || text.contains("拍照") || text.contains("打卡")) themes.add("网红");
        if (text.contains("休闲") || text.contains("放松") || text.contains("慢逛") || text.contains("别太累")) themes.add("休闲");
        return new ArrayList<>(themes);
    }

    private String parseCompanion(String text) {
        if (text.contains("一个人") || text.contains("独自") || text.contains("自己")) return "独自";
        if (text.contains("女朋友") || text.contains("男朋友") || text.contains("情侣") || text.contains("对象")) return "情侣";
        if (text.contains("孩子") || text.contains("亲子") || text.contains("家人") || text.contains("家庭")) return "亲子";
        if (text.contains("朋友") || text.contains("同学") || text.contains("同事")) return "朋友";
        return null;
    }

    private String parseWalkingLevel(String text) {
        if (text.contains("别太累") || text.contains("少走") || text.contains("不累") || text.contains("轻松")) return "低";
        if (text.contains("暴走") || text.contains("能走") || text.contains("多走") || text.contains("爬山")) return "高";
        return null;
    }

    private List<String> loadPoiNameHints(String cityName) {
        try {
            String cityCode = resolveCityCode(cityName);
            List<Poi> pois = poiMapper.selectPlanningCandidates(false, null, cityCode, cityName, POI_HINT_LIMIT);
            if (pois == null || pois.isEmpty()) {
                return List.of();
            }
            Set<String> names = new LinkedHashSet<>();
            for (Poi poi : pois) {
                if (poi != null && StringUtils.hasText(poi.getName())) {
                    names.add(poi.getName().trim());
                }
            }
            return new ArrayList<>(names);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void sanitizeParsedResult(SmartFillVO parsed,
                                      String text,
                                      List<String> poiHints,
                                      String cityHint) {
        parsed.setThemes(filterAllowedThemes(parsed.getThemes()));
        parsed.setBudgetLevel(normalizeAllowed(parsed.getBudgetLevel(), ALLOWED_BUDGETS));
        parsed.setWalkingLevel(normalizeAllowed(parsed.getWalkingLevel(), ALLOWED_WALKING));
        parsed.setCompanionType(normalizeAllowed(parsed.getCompanionType(), ALLOWED_COMPANION));
        parsed.setStartTime(normalizeTime(parsed.getStartTime()));
        parsed.setEndTime(normalizeTime(parsed.getEndTime()));
        parsed.setTripDays(normalizeTripDays(parsed.getTripDays()));
        parsed.setCityName(resolveCityName(parsed.getCityName(), cityHint));
        parsed.setMustVisitPoiNames(resolveMustVisitPoiNames(parsed.getMustVisitPoiNames(), text, poiHints, parsed.getCityName()));
        parsed.setDepartureText(trimToNull(parsed.getDepartureText()));
        parsed.setDepartureCandidates(normalizeCandidateList(parsed.getDepartureCandidates()));

        if (!isValidCoordinate(parsed.getDepartureLatitude(), parsed.getDepartureLongitude())) {
            parsed.setDepartureLatitude(null);
            parsed.setDepartureLongitude(null);
        }

        parsed.setSummary(resolveSummary(parsed));
    }

    private List<String> filterAllowedThemes(List<String> themes) {
        if (themes == null || themes.isEmpty()) {
            return List.of();
        }
        Set<String> filtered = new LinkedHashSet<>();
        for (String item : themes) {
            if (StringUtils.hasText(item) && ALLOWED_THEMES.contains(item.trim())) {
                filtered.add(item.trim());
            }
        }
        return new ArrayList<>(filtered);
    }

    private String normalizeAllowed(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return allowed.contains(normalized) ? normalized : null;
    }

    private String normalizeTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return TIME_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private Double normalizeTripDays(Double tripDays) {
        if (tripDays == null) {
            return null;
        }
        if (Math.abs(tripDays - 0.5D) < 0.01D) {
            return 0.5D;
        }
        if (Math.abs(tripDays - 2.0D) < 0.01D) {
            return 2.0D;
        }
        if (Math.abs(tripDays - 1.0D) < 0.01D) {
            return 1.0D;
        }
        return null;
    }

    private List<String> resolveMustVisitPoiNames(List<String> fromModel,
                                                  String text,
                                                  List<String> poiHints,
                                                  String cityName) {
        Set<String> resolved = new LinkedHashSet<>();
        String ifsCanonicalName = resolveIfsCanonicalName(poiHints);

        if (fromModel != null) {
            for (String item : fromModel) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                String normalized = normalizePoiByAlias(item.trim(), ifsCanonicalName);
                normalized = normalizeByDisambiguation(normalized, cityName);
                if (StringUtils.hasText(normalized)) {
                    resolved.add(normalized);
                }
            }
        }

        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String alias : IFS_ALIAS_SKILL) {
            if (lower.contains(alias)) {
                resolved.add(ifsCanonicalName);
                break;
            }
        }
        resolved.addAll(resolveIntentPoiNames(text, poiHints));

        if (poiHints != null && text != null) {
            for (String poiName : poiHints) {
                if (!StringUtils.hasText(poiName)) {
                    continue;
                }
                if (text.contains(poiName)) {
                    resolved.add(normalizeByDisambiguation(poiName.trim(), cityName));
                }
            }
        }

        if (resolved.isEmpty() && containsIfsAlias(lower)) {
            resolved.add(ifsCanonicalName);
        }

        return new ArrayList<>(resolved);
    }

    private List<String> resolveIntentPoiNames(String text, List<String> poiHints) {
        if (!containsPandaIntent(text)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        String pandaBase = resolvePreferredPoiName(poiHints, "大熊猫", "繁育");
        if (!StringUtils.hasText(pandaBase)) {
            pandaBase = resolvePreferredPoiName(poiHints, "熊猫", "基地");
        }
        names.add(StringUtils.hasText(pandaBase) ? pandaBase : PANDA_BASE_FALLBACK_NAME);

        String zoo = resolvePreferredPoiName(poiHints, "动物园");
        if (StringUtils.hasText(zoo)) {
            names.add(zoo);
        } else if (text != null && text.contains("动物园")) {
            names.add(ZOO_FALLBACK_NAME);
        }
        return new ArrayList<>(names);
    }

    private boolean containsPandaIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : PANDA_INTENT_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String resolvePreferredPoiName(List<String> poiHints, String... keywords) {
        if (poiHints == null || poiHints.isEmpty() || keywords == null || keywords.length == 0) {
            return null;
        }
        for (String poiName : poiHints) {
            if (!StringUtils.hasText(poiName)) {
                continue;
            }
            boolean matched = true;
            for (String keyword : keywords) {
                if (!poiName.contains(keyword)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return poiName.trim();
            }
        }
        return null;
    }

    private void enrichGeoFields(SmartFillVO parsed, String text) {
        String cityName = resolveCityName(parsed.getCityName(), text);
        parsed.setCityName(cityName);

        if (!StringUtils.hasText(parsed.getDepartureText())) {
            parsed.setDepartureText(extractDepartureText(text));
        }

        if (!StringUtils.hasText(parsed.getDepartureText())) {
            return;
        }

        PlaceDisambiguationService.PlaceResolution departureResolution = placeDisambiguationService == null
                ? PlaceDisambiguationService.PlaceResolution.empty()
                : placeDisambiguationService.disambiguate(parsed.getDepartureText(), cityName, null);

        if (departureResolution.candidates() != null && !departureResolution.candidates().isEmpty()) {
            parsed.setDepartureCandidates(departureResolution.candidates().stream()
                    .map(PlaceDisambiguationService.ResolvedPlace::canonicalName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(3)
                    .toList());
        }

        if (departureResolution.best() != null) {
            PlaceDisambiguationService.ResolvedPlace best = departureResolution.best();
            if (StringUtils.hasText(best.canonicalName())) {
                parsed.setDepartureText(best.canonicalName());
            }
            if (!departureResolution.clarificationRequired()
                    && best.latitude() != null
                    && best.longitude() != null
                    && isValidCoordinate(best.latitude().doubleValue(), best.longitude().doubleValue())) {
                parsed.setDepartureLatitude(best.latitude().doubleValue());
                parsed.setDepartureLongitude(best.longitude().doubleValue());
                return;
            }
        }

        if (!isValidCoordinate(parsed.getDepartureLatitude(), parsed.getDepartureLongitude()) && geoSearchService != null) {
            GeoPoint point = geoSearchService.geocode(parsed.getDepartureText(), cityName).orElse(null);
            if (point != null && point.valid()) {
                parsed.setDepartureLatitude(point.latitude().doubleValue());
                parsed.setDepartureLongitude(point.longitude().doubleValue());
            }
        }
    }

    private List<String> normalizeCandidateList(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String candidate : candidates) {
            String value = trimToNull(candidate);
            if (value != null) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizePoiByAlias(String value, String ifsCanonicalName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (containsIfsAlias(lower)) {
            return ifsCanonicalName;
        }
        return value;
    }

    private boolean containsIfsAlias(String lower) {
        if (!StringUtils.hasText(lower)) {
            return false;
        }
        for (String alias : IFS_ALIAS_SKILL) {
            if (lower.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private String resolveIfsCanonicalName(List<String> poiHints) {
        if (poiHints != null) {
            for (String poiName : poiHints) {
                if (!StringUtils.hasText(poiName)) {
                    continue;
                }
                String normalized = poiName.trim();
                if (normalized.toLowerCase(Locale.ROOT).contains("ifs")) {
                    return normalized;
                }
            }
        }
        return IFS_FALLBACK_NAME;
    }

    private String normalizeByDisambiguation(String keyword, String cityName) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        if (placeDisambiguationService == null) {
            return keyword;
        }
        PlaceDisambiguationService.PlaceResolution resolution = placeDisambiguationService.disambiguate(keyword, cityName, null);
        if (resolution.best() == null) {
            return keyword;
        }
        return resolution.best().canonicalName();
    }

    private String extractDepartureText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher fromMatcher = DEPARTURE_FROM_PATTERN.matcher(text);
        if (fromMatcher.find()) {
            return trimToNull(fromMatcher.group(1));
        }
        Matcher stayMatcher = DEPARTURE_STAY_PATTERN.matcher(text);
        if (stayMatcher.find()) {
            return trimToNull(stayMatcher.group(1));
        }
        return null;
    }

    private String resolveCityName(String fromPayload, String textHint) {
        String guessed = cityResolverService == null ? null : cityResolverService.guessCityNameFromText(textHint);
        if (cityResolverService != null) {
            return cityResolverService.resolveCityName(fromPayload, resolveCityCode(guessed));
        }
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload.trim();
        }
        if (StringUtils.hasText(guessed)) {
            return guessed.trim();
        }
        return "成都";
    }

    private String resolveCityCode(String cityName) {
        if (cityResolverService == null) {
            return null;
        }
        return cityResolverService.resolveCityCode(null, cityName);
    }

    private boolean isValidCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return Math.abs(latitude) <= 90D && Math.abs(longitude) <= 180D;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> resolveSummary(SmartFillVO parsed) {
        Set<String> summary = new LinkedHashSet<>();
        if (parsed.getSummary() != null) {
            for (String item : parsed.getSummary()) {
                if (StringUtils.hasText(item)) {
                    summary.add(item.trim());
                }
            }
        }

        if (parsed.getMustVisitPoiNames() != null && !parsed.getMustVisitPoiNames().isEmpty()) {
            summary.add("必去：" + String.join("、", parsed.getMustVisitPoiNames()));
        }
        if (StringUtils.hasText(parsed.getDepartureText())) {
            summary.add("出发地：" + parsed.getDepartureText());
        }
        if (summary.isEmpty() && parsed.getThemes() != null) {
            summary.addAll(parsed.getThemes());
        }
        if (summary.isEmpty() && parsed.getCompanionType() != null) {
            summary.add(parsed.getCompanionType());
        }

        return new ArrayList<>(summary);
    }
}
