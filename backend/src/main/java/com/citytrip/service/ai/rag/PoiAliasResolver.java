package com.citytrip.service.ai.rag;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PoiAliasResolver {
    private static final Map<String, List<String>> FIXED_ALIASES = buildFixedAliases();
    private static final Set<String> GENERIC_SUFFIXES = Set.of(
            "\u535a\u7269\u9986", "\u7f8e\u672f\u9986", "\u52a8\u7269\u56ed", "\u690d\u7269\u56ed", "\u516c\u56ed", "\u53e4\u9547",
            "\u666f\u533a", "\u57fa\u5730", "\u5e7f\u573a", "\u5546\u573a", "\u8d2d\u7269\u4e2d\u5fc3", "\u6b65\u884c\u8857"
    );

    public List<String> expand(String keyword, String cityName) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        String normalizedKeyword = keyword.trim();
        values.add(normalizedKeyword);

        List<String> fixedAliases = FIXED_ALIASES.get(normalizedKeyword);
        if (fixedAliases != null) {
            values.addAll(fixedAliases);
        }

        if (StringUtils.hasText(cityName)) {
            String normalizedCity = cityName.trim();
            if (shouldAddCityPrefix(normalizedKeyword, normalizedCity)) {
                values.add(normalizedCity + normalizedKeyword);
            }
        }
        return new ArrayList<>(values);
    }

    private boolean shouldAddCityPrefix(String keyword, String cityName) {
        if (!StringUtils.hasText(keyword) || !StringUtils.hasText(cityName)) {
            return false;
        }
        if (keyword.startsWith(cityName)) {
            return false;
        }
        return GENERIC_SUFFIXES.stream().anyMatch(keyword::endsWith);
    }

    private static Map<String, List<String>> buildFixedAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("\u4e07\u8c61\u57ce", List.of("\u6210\u90fd\u4e07\u8c61\u57ce", "\u534e\u6da6\u4e07\u8c61\u57ce", "\u4e07\u8c61\u57ce\u8d2d\u7269\u4e2d\u5fc3"));
        aliases.put("\u56fd\u91d1", List.of("IFS\u56fd\u9645\u91d1\u878d\u4e2d\u5fc3"));
        aliases.put("\u81ea\u7136\u535a\u7269\u9986", List.of("\u6210\u90fd\u81ea\u7136\u535a\u7269\u9986"));
        aliases.put("\u81ea\u7136\u9986", List.of("\u6210\u90fd\u81ea\u7136\u535a\u7269\u9986"));
        aliases.put("\u52a8\u7269\u56ed", List.of("\u6210\u90fd\u52a8\u7269\u56ed"));
        aliases.put("\u718a\u732b\u57fa\u5730", List.of("\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730", "\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730"));
        aliases.put("\u6210\u90fd\u535a\u7269\u9986", List.of("\u6210\u90fd\u535a\u7269\u9986"));
        aliases.put("\u5bbd\u7a84\u5df7\u5b50", List.of("\u6210\u90fd\u5bbd\u7a84\u5df7\u5b50"));
        aliases.put("\u592a\u53e4\u91cc", List.of("\u6210\u90fd\u592a\u53e4\u91cc", "\u8fdc\u6d0b\u592a\u53e4\u91cc"));
        aliases.put("\u6625\u7199\u8def", List.of("\u6210\u90fd\u6625\u7199\u8def"));
        aliases.put("\u4e1c\u90ca\u8bb0\u5fc6", List.of("\u6210\u90fd\u4e1c\u90ca\u8bb0\u5fc6"));
        aliases.put("\u9752\u57ce\u5c71", List.of("\u6210\u90fd\u9752\u57ce\u5c71"));
        aliases.put("\u90fd\u6c5f\u5830", List.of("\u6210\u90fd\u90fd\u6c5f\u5830\u666f\u533a"));
        aliases.put("\u73af\u7403\u4e2d\u5fc3", List.of("\u65b0\u4e16\u7eaa\u73af\u7403\u4e2d\u5fc3", "\u6210\u90fd\u73af\u7403\u4e2d\u5fc3"));
        return aliases;
    }
}
