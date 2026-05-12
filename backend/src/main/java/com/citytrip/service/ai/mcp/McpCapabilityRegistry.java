package com.citytrip.service.ai.mcp;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class McpCapabilityRegistry {
    private final Map<String, McpCapability> capabilities = new LinkedHashMap<>();

    public McpCapabilityRegistry(List<McpCapability> capabilities) {
        if (capabilities == null) {
            return;
        }
        for (McpCapability capability : capabilities) {
            if (capability == null || !org.springframework.util.StringUtils.hasText(capability.capabilityName())) {
                continue;
            }
            this.capabilities.put(capability.capabilityName(), capability);
        }
    }

    public Optional<McpCapability> find(String name) {
        return Optional.ofNullable(capabilities.get(name));
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(capabilities.keySet());
    }
}
