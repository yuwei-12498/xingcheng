package com.citytrip.service.ai.mcp;

import java.util.Optional;

public interface McpCapability {

    String capabilityName();

    Object execute(Object input);

    default Optional<String> description() {
        return Optional.empty();
    }
}
