package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.PoiService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Order(40)
public class PoiSearchSkill extends AbstractGeoSkill {

    private static final String[] VISIT_PREFIXES = {
            "\u6211\u60f3\u53bb", "\u60f3\u53bb", "\u6211\u60f3\u901b", "\u60f3\u901b", "\u6211\u60f3\u770b\u770b", "\u60f3\u770b\u770b", "\u53bb", "\u770b\u770b", "\u6253\u5361"
    };
    private static final String[] VISIT_SUFFIXES = {
            "\u73a9\u4e00\u73a9", "\u901b\u4e00\u901b", "\u770b\u4e00\u770b", "\u73a9", "\u901b", "\u770b\u770b", "\u6253\u5361", "\u65c5\u6e38", "\u62cd\u7167", "\u600e\u4e48\u6837", "\u503c\u5f97\u5417", "\u53ef\u4ee5\u5417", "\u5417", "\u5440", "\u554a", "\u5462"
    };

    private final PoiService poiService;

    public PoiSearchSkill(PoiService poiService) {
        this.poiService = poiService;
    }

    @Override
    public String skillName() {
        return "poi_search";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        String question = questionOf(req);
        boolean explicitSearchIntent = containsAny(question, "\u627e", "\u641c\u7d22", "\u641c");
        boolean visitIntent = extractVisitKeyword(question) != null;
        return (explicitSearchIntent || visitIntent)
                && !containsAny(question, "\u9644\u8fd1", "\u5468\u8fb9", "\u65c1\u8fb9")
                && !containsAny(question, "\u9152\u5e97", "\u4f4f\u5bbf")
                && !containsAny(question, "\u6362\u6210", "\u66ff\u6362", "\u6362\u6389");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String city = cityOf(req);
        String keyword = resolveKeyword(questionOf(req));
        List<ChatSkillPayloadVO.ResultItem> items = fromPoiResults(poiService.searchLive(keyword == null ? "" : keyword, city, 5));
        String source = items.isEmpty() ? "local-db" : items.get(0).getSource();
        ChatSkillPayloadVO payload = buildPayload(
                skillName(),
                "poi_search",
                city,
                keyword,
                "poi",
                5,
                0,
                items,
                source,
                items.isEmpty()
                        ? "\u6682\u65f6\u6ca1\u6709\u627e\u5230\u5339\u914d\u5730\u70b9\uff0c\u4f60\u53ef\u4ee5\u6362\u4e2a\u66f4\u5177\u4f53\u7684\u540d\u79f0\u3002"
                        : "\u6211\u5148\u5e2e\u4f60\u5217\u51fa\u5b9e\u65f6\u627e\u5230\u7684\u5730\u70b9\u7ed3\u679c\u3002"
        );
        payload.getQuery().setKeyword(keyword);
        return payload;
    }

    private String resolveKeyword(String question) {
        String visitKeyword = extractVisitKeyword(question);
        if (StringUtils.hasText(visitKeyword)) {
            return visitKeyword;
        }
        String keyword = extractPoiKeyword(question);
        return StringUtils.hasText(keyword) ? keyword : visitKeyword;
    }

    private String extractVisitKeyword(String question) {
        String normalized = trimToNull(question);
        if (normalized == null) {
            return null;
        }
        for (String prefix : VISIT_PREFIXES) {
            if (StringUtils.hasText(prefix) && normalized.startsWith(prefix)) {
                String candidate = trimToNull(normalized.substring(prefix.length()));
                candidate = stripVisitSuffix(candidate);
                if (!StringUtils.hasText(candidate)) {
                    return null;
                }
                if (containsAny(candidate, "\u54ea\u91cc", "\u54ea\u513f", "\u4ec0\u4e48", "\u9644\u8fd1", "\u5468\u8fb9", "\u9152\u5e97", "\u4f4f\u5bbf", "\u6362\u6210", "\u66ff\u6362", "\u6362\u6389")) {
                    return null;
                }
                if (candidate.length() < 2 || candidate.length() > 24) {
                    return null;
                }
                return candidate;
            }
        }
        return null;
    }

    private String stripVisitSuffix(String candidate) {
        String value = trimToNull(candidate);
        if (value == null) {
            return null;
        }
        boolean changed;
        do {
            changed = false;
            for (String suffix : VISIT_SUFFIXES) {
                if (StringUtils.hasText(suffix) && value.endsWith(suffix)) {
                    value = trimToNull(value.substring(0, value.length() - suffix.length()));
                    changed = true;
                    break;
                }
            }
        } while (changed && value != null);
        return value;
    }
}
