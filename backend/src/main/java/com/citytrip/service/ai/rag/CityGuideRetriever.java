package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CityGuideRetriever implements ContextRetriever {
    private static final Map<String, CityGuideFact> KNOWLEDGE = buildKnowledge();

    @Override
    public List<RetrievalDocument> retrieve(AiExecutionContext context) {
        if (context == null || !StringUtils.hasText(context.getUserInput())) {
            return List.of();
        }
        String normalizedInput = normalize(context.getUserInput());
        List<RetrievalDocument> documents = new ArrayList<>();
        for (CityGuideFact fact : KNOWLEDGE.values()) {
            if (fact.matches(normalizedInput)) {
                documents.add(new RetrievalDocument("city-guide", fact.evidence()));
            }
        }
        return documents;
    }

    private static Map<String, CityGuideFact> buildKnowledge() {
        Map<String, CityGuideFact> knowledge = new LinkedHashMap<>();
        knowledge.put("wanxiangcheng", new CityGuideFact(
                List.of("成都万象城", "万象城"),
                "成都万象城常见简称就是万象城，搜索时也可以试试华润万象城或万象城购物中心"
        ));
        knowledge.put("guojin", new CityGuideFact(
                List.of("IFS国际金融中心", "IFS", "国金"),
                "成都用户常把IFS国际金融中心简称为国金，外部搜索时用IFS国际金融中心命中会更稳"
        ));
        knowledge.put("taikooli", new CityGuideFact(
                List.of("太古里", "成都太古里"),
                "太古里更适合下午到夜间连着逛，吃饭和拍照尽量错开晚高峰"
        ));
        knowledge.put("chunxilu", new CityGuideFact(
                List.of("春熙路"),
                "春熙路夜间人流会更密，和太古里连走时建议把热门拍照点放前半段"
        ));
        knowledge.put("qingchengshan", new CityGuideFact(
                List.of("青城山"),
                "青城山适合早点出发，返程时间最好预留得更宽一些"
        ));
        knowledge.put("dujiangyan", new CityGuideFact(
                List.of("都江堰"),
                "都江堰景区步行范围不小，和青城山放同一天时要控制节奏"
        ));
        return knowledge;
    }

    private static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。！？；;:：“”\"'‘’（）()\\[\\]{}<>《》·~`_-]+", "");
    }

    private record CityGuideFact(List<String> aliases, String evidence) {
        private boolean matches(String normalizedInput) {
            if (!StringUtils.hasText(normalizedInput) || aliases == null || aliases.isEmpty()) {
                return false;
            }
            for (String alias : aliases) {
                if (normalizedInput.contains(normalize(alias))) {
                    return true;
                }
            }
            return false;
        }
    }
}
