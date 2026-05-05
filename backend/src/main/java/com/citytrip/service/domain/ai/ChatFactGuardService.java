package com.citytrip.service.domain.ai;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatFactGuardService {

    public GuardResult guard(String answer, List<ChatGeoSkillService.GeoFact> facts) {
        String normalizedAnswer = StringUtils.hasText(answer) ? answer.trim() : "";
        List<String> evidence = buildEvidenceCards(facts);
        if (!StringUtils.hasText(normalizedAnswer) || facts == null || facts.isEmpty()) {
            return new GuardResult(normalizedAnswer, evidence, false);
        }

        Set<String> evidenceNames = facts.stream()
                .map(ChatGeoSkillService.GeoFact::name)
                .filter(StringUtils::hasText)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        Set<String> suspicious = extractMentionedPlaces(normalizedAnswer).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .filter(name -> !containsEvidence(name, evidenceNames))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        if (suspicious.isEmpty()) {
            return new GuardResult(normalizedAnswer, evidence, false);
        }

        String suffix = "（以下地点信息未核验：" + String.join("、", suspicious) + "，建议以地图为准）";
        String guardedAnswer = normalizedAnswer + (normalizedAnswer.endsWith("。") ? "" : "。") + suffix;
        return new GuardResult(guardedAnswer, evidence, true);
    }

    private List<String> buildEvidenceCards(List<ChatGeoSkillService.GeoFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cards = new ArrayList<>();
        for (ChatGeoSkillService.GeoFact fact : facts) {
            if (fact == null || !StringUtils.hasText(fact.name())) {
                continue;
            }
            StringBuilder card = new StringBuilder(fact.name());
            List<String> tags = new ArrayList<>();
            if (StringUtils.hasText(fact.category())) {
                tags.add(fact.category());
            }
            if (fact.distanceMeters() != null) {
                tags.add("约" + fact.distanceMeters() + "m");
            }
            if (StringUtils.hasText(fact.source())) {
                tags.add(fact.source());
            }
            if (!tags.isEmpty()) {
                card.append("（").append(String.join(" / ", tags)).append("）");
            }
            cards.add(card.toString());
        }
        return cards.size() > 8 ? cards.subList(0, 8) : cards;
    }

    private boolean containsEvidence(String mention, Set<String> evidenceNames) {
        if (!StringUtils.hasText(mention) || evidenceNames == null || evidenceNames.isEmpty()) {
            return false;
        }
        for (String evidence : evidenceNames) {
            if (evidence.contains(mention) || mention.contains(evidence)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractMentionedPlaces(String answer) {
        if (!StringUtils.hasText(answer)) {
            return Collections.emptyList();
        }
        Set<String> places = new LinkedHashSet<>();
        String[] chunks = answer.split("[，。！？；、\\s]+");
        for (String chunk : chunks) {
            if (!StringUtils.hasText(chunk)) {
                continue;
            }
            String token = chunk.trim();
            if (token.length() < 2 || token.length() > 20) {
                continue;
            }
            if (looksLikePlaceName(token)) {
                places.add(token);
            }
        }
        return new ArrayList<>(places);
    }

    private boolean looksLikePlaceName(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalized = token.trim();
        if (normalized.toUpperCase(Locale.ROOT).contains("IFS")) {
            return true;
        }
        String[] suffixes = {
                "路", "街", "巷", "站", "中心", "公园", "景区", "山", "湖", "寺", "馆", "广场", "大厦"
        };
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public record GuardResult(String answer, List<String> evidence, boolean degraded) {
    }
}
