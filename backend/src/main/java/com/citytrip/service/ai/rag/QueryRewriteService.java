package com.citytrip.service.ai.rag;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QueryRewriteService {
    private final PoiAliasResolver aliasResolver;

    public QueryRewriteService(PoiAliasResolver aliasResolver) {
        this.aliasResolver = aliasResolver;
    }

    public List<String> rewrite(String keyword, String cityName) {
        List<String> queries = new ArrayList<>(aliasResolver.expand(keyword, cityName));
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (containsAny(lower, "\u4e07\u8c61\u57ce", "\u592a\u53e4\u91cc", "ifs", "\u73af\u7403\u4e2d\u5fc3", "\u5546\u573a", "\u8d2d\u7269")) {
                queries.add(normalized + " \u5546\u573a");
                queries.add(normalized + " \u8d2d\u7269\u4e2d\u5fc3");
            }
            if (containsAny(lower, "\u535a\u7269\u9986", "\u7f8e\u672f\u9986", "\u5c55\u9986")) {
                queries.add(normalized + " \u5c55\u9986");
                queries.add(normalized + " \u573a\u9986");
            }
            if (containsAny(lower, "\u52a8\u7269\u56ed", "\u718a\u732b", "\u57fa\u5730", "\u666f\u533a", "\u516c\u56ed")) {
                queries.add(normalized + " \u666f\u533a");
            }
        }
        return queries.stream().distinct().toList();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
