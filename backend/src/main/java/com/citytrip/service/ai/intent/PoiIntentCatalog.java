package com.citytrip.service.ai.intent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * 首页智能填充和模型兜底共用的 POI 意图词表。
 *
 * <p>这里放的是“用户明确要找哪类消费/休闲点位”的强意图，不替代模型理解，
 * 只在模型漏填或泛化错误时补上可验证的类目边界。</p>
 */
public final class PoiIntentCatalog {

    private static final int MAX_HINTS = 12;

    private static final List<String> FOOD_EXCLUDED = List.of(
            "博物馆", "五金", "家具", "家装", "装修材料", "室内装修材料", "建材", "门窗", "纱窗", "材料零售"
    );

    private static final List<String> LEISURE_SERVICE_EXCLUDED = List.of(
            "博物馆", "景区", "公园", "五金", "家具", "家装", "装修材料", "室内装修材料", "建材", "门窗", "纱窗", "材料零售"
    );

    private static final List<PoiIntentRule> RULES = List.of(
            new PoiIntentRule(
                    List.of("火锅"),
                    List.of("美食"),
                    List.of("火锅", "餐饮", "美食"),
                    FOOD_EXCLUDED,
                    List.of("火锅")
            ),
            new PoiIntentRule(
                    List.of("烤肉", "烧烤", "烤串", "bbq", "barbecue"),
                    List.of("美食"),
                    List.of("烤肉", "烧烤", "餐饮", "美食"),
                    FOOD_EXCLUDED,
                    List.of("烤肉", "烧烤")
            ),
            new PoiIntentRule(
                    List.of("小吃", "夜市"),
                    List.of("美食"),
                    List.of("小吃", "餐饮", "美食"),
                    FOOD_EXCLUDED,
                    List.of("小吃", "夜市")
            ),
            new PoiIntentRule(
                    List.of("咖啡", "咖啡馆", "cafe"),
                    List.of("美食", "休闲"),
                    List.of("咖啡", "餐饮", "美食"),
                    FOOD_EXCLUDED,
                    List.of("咖啡")
            ),
            new PoiIntentRule(
                    List.of("网吧", "网咖", "电竞馆", "电竞", "打游戏", "上网"),
                    List.of("休闲"),
                    List.of("网吧", "网咖", "电竞", "娱乐"),
                    LEISURE_SERVICE_EXCLUDED,
                    List.of("网吧", "网咖", "电竞")
            ),
            new PoiIntentRule(
                    List.of("洗浴", "洗澡", "足浴", "按摩", "spa", "汤泉", "温泉", "汗蒸"),
                    List.of("休闲"),
                    List.of("洗浴", "足浴", "按摩", "SPA", "汤泉", "温泉", "休闲"),
                    LEISURE_SERVICE_EXCLUDED,
                    List.of("洗浴", "足浴", "按摩", "汤泉", "温泉")
            ),
            new PoiIntentRule(
                    List.of("ktv", "唱歌", "台球", "棋牌", "麻将", "桌游", "剧本杀", "密室", "电玩城"),
                    List.of("休闲"),
                    List.of("娱乐", "休闲", "KTV", "台球", "棋牌", "剧本杀", "密室"),
                    LEISURE_SERVICE_EXCLUDED,
                    List.of("KTV", "台球", "棋牌", "剧本杀", "密室")
            )
    );

    private PoiIntentCatalog() {
    }

    public static List<String> themes(String text) {
        return collect(text, PoiIntentRule::themes);
    }

    public static List<String> preferredCategories(String text) {
        return collect(text, PoiIntentRule::preferredCategories);
    }

    public static List<String> excludedCategories(String text) {
        return collect(text, PoiIntentRule::excludedCategories);
    }

    public static List<String> alternativeHints(String text) {
        return collect(text, PoiIntentRule::alternativeHints);
    }

    public static boolean matchesAnyIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return RULES.stream().anyMatch(rule -> rule.matches(text));
    }

    private static List<String> collect(String text, Function<PoiIntentRule, List<String>> extractor) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (PoiIntentRule rule : RULES) {
            if (!rule.matches(text)) {
                continue;
            }
            for (String item : extractor.apply(rule)) {
                if (StringUtils.hasText(item)) {
                    values.add(item.trim());
                }
                if (values.size() >= MAX_HINTS) {
                    return new ArrayList<>(values);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private record PoiIntentRule(List<String> triggers,
                                 List<String> themes,
                                 List<String> preferredCategories,
                                 List<String> excludedCategories,
                                 List<String> alternativeHints) {

        private boolean matches(String text) {
            if (!StringUtils.hasText(text)) {
                return false;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            for (String trigger : triggers) {
                if (StringUtils.hasText(trigger) && normalized.contains(trigger.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
