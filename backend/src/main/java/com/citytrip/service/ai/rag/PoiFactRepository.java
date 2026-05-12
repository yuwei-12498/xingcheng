package com.citytrip.service.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PoiFactRepository {
    private static final String FACT_RESOURCE_PATTERN = "classpath*:ai/poi-facts/**/*.json";
    private static final TypeReference<List<PoiFactRecord>> FACT_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourcePatternResolver;
    private volatile List<PoiFactRecord> cachedFacts;

    public PoiFactRepository() {
        this(new ObjectMapper().findAndRegisterModules(), new PathMatchingResourcePatternResolver());
    }

    public PoiFactRepository(ObjectMapper objectMapper) {
        this(objectMapper, new PathMatchingResourcePatternResolver());
    }

    public PoiFactRepository(ObjectMapper objectMapper,
                             ResourcePatternResolver resourcePatternResolver) {
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.resourcePatternResolver = resourcePatternResolver == null
                ? new PathMatchingResourcePatternResolver()
                : resourcePatternResolver;
    }

    public List<PoiFactRecord> loadAll() {
        List<PoiFactRecord> snapshot = cachedFacts;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (cachedFacts == null) {
                cachedFacts = List.copyOf(loadFromResources());
            }
            return cachedFacts;
        }
    }

    private List<PoiFactRecord> loadFromResources() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(FACT_RESOURCE_PATTERN);
            Arrays.sort(resources, Comparator.comparing(Resource::getDescription));
            List<PoiFactRecord> result = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                result.addAll(readResource(resource));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load POI fact resources", e);
        }
    }

    private List<PoiFactRecord> readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            List<PoiFactRecord> facts = objectMapper.readValue(inputStream, FACT_LIST_TYPE);
            if (facts == null || facts.isEmpty()) {
                return List.of();
            }
            String categoryFile = resolveCategoryFile(resource);
            List<PoiFactRecord> normalized = new ArrayList<>(facts.size());
            for (PoiFactRecord fact : facts) {
                if (fact == null || !StringUtils.hasText(fact.poiName()) || !StringUtils.hasText(fact.content())) {
                    continue;
                }
                normalized.add(withCategoryFallback(fact, categoryFile));
            }
            return normalized;
        }
    }

    private PoiFactRecord withCategoryFallback(PoiFactRecord fact, String categoryFile) {
        return new PoiFactRecord(
                fact.id(),
                fact.city(),
                StringUtils.hasText(fact.categoryFile()) ? fact.categoryFile() : categoryFile,
                fact.poiName(),
                fact.aliases(),
                fact.factType(),
                fact.content(),
                fact.sourceName(),
                fact.sourceUrl(),
                fact.updatedAt(),
                fact.tags(),
                fact.weight()
        );
    }

    private String resolveCategoryFile(Resource resource) {
        String filename = resource.getFilename();
        if (!StringUtils.hasText(filename)) {
            return "unknown";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}
