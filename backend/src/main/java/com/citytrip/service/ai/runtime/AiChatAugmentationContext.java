package com.citytrip.service.ai.runtime;

import java.util.List;

public record AiChatAugmentationContext(List<String> ragDocuments,
                                        List<String> toolPayloads,
                                        List<String> mcpEvidence,
                                        List<String> evidence) {

    public AiChatAugmentationContext {
        ragDocuments = ragDocuments == null ? List.of() : List.copyOf(ragDocuments);
        toolPayloads = toolPayloads == null ? List.of() : List.copyOf(toolPayloads);
        mcpEvidence = mcpEvidence == null ? List.of() : List.copyOf(mcpEvidence);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static AiChatAugmentationContext empty() {
        return new AiChatAugmentationContext(List.of(), List.of(), List.of(), List.of());
    }

    public static AiChatAugmentationContext of(List<String> ragDocuments,
                                               List<String> toolPayloads,
                                               List<String> mcpEvidence,
                                               List<String> evidence) {
        return new AiChatAugmentationContext(ragDocuments, toolPayloads, mcpEvidence, evidence);
    }

    public boolean hasAnyContext() {
        return !ragDocuments.isEmpty() || !toolPayloads.isEmpty() || !mcpEvidence.isEmpty();
    }
}
