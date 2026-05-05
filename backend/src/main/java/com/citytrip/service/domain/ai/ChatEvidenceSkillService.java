package com.citytrip.service.domain.ai;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatEvidenceSkillService {

    private static final int MAX_EVIDENCE_COUNT = 12;

    public List<String> mergeEvidence(List<String> guardEvidence,
                                      ChatRouteContextSkillService.RouteContext routeContext,
                                      Set<String> usedSkills,
                                      String firstLegSource,
                                      List<String> skillEvidence) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        if (usedSkills != null && !usedSkills.isEmpty()) {
            String skillTag = usedSkills.stream()
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(","));
            if (StringUtils.hasText(skillTag)) {
                merged.add("skills=" + skillTag);
            }
        }

        if (routeContext != null && routeContext.available()) {
            if (StringUtils.hasText(routeContext.selectedOptionKey())) {
                merged.add("route_option=" + routeContext.selectedOptionKey());
            }
            merged.add("route_nodes=" + routeContext.nodes().size());
        }

        if (StringUtils.hasText(firstLegSource)) {
            merged.add("first_leg_source=" + firstLegSource.trim());
        }

        appendAll(merged, skillEvidence);
        appendAll(merged, guardEvidence);

        List<String> result = new ArrayList<>(merged);
        return result.size() > MAX_EVIDENCE_COUNT ? result.subList(0, MAX_EVIDENCE_COUNT) : result;
    }

    private void appendAll(LinkedHashSet<String> merged, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            merged.add(item.trim());
            if (merged.size() >= MAX_EVIDENCE_COUNT) {
                return;
            }
        }
    }
}

