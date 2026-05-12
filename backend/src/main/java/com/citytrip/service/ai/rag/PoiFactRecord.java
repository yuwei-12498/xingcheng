package com.citytrip.service.ai.rag;

import java.util.List;

public record PoiFactRecord(String id,
                            String city,
                            String categoryFile,
                            String poiName,
                            List<String> aliases,
                            String factType,
                            String content,
                            String sourceName,
                            String sourceUrl,
                            String updatedAt,
                            List<String> tags,
                            double weight) {

    public PoiFactRecord {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        tags = tags == null ? List.of() : List.copyOf(tags);
        weight = weight <= 0D ? 1.0D : weight;
    }
}
