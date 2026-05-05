package com.citytrip.service.impl.vivo;

import java.util.function.Function;

public record VivoToolDefinition(
        String name,
        String description,
        String parametersJsonSchema,
        Function<String, String> executor
) {
}
