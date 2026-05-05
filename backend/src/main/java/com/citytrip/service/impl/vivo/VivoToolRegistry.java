package com.citytrip.service.impl.vivo;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class VivoToolRegistry {

    private final Map<String, VivoToolDefinition> tools = new LinkedHashMap<>();

    public void register(VivoToolDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("Tool definition name must not be blank");
        }
        tools.put(definition.name(), definition);
    }

    public Optional<VivoToolDefinition> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<VivoToolDefinition> list() {
        return new ArrayList<>(tools.values());
    }
}
