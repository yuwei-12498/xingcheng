package com.citytrip.service.domain.scoring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ScoreBreakdown(double total, Map<String, Double> components) {

    public ScoreBreakdown {
        components = components == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    public double component(String name) {
        return components.getOrDefault(name, 0D);
    }
}