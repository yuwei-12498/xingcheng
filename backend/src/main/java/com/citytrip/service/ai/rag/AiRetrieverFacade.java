package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;

import java.util.ArrayList;
import java.util.List;

public class AiRetrieverFacade {
    private final List<ContextRetriever> retrievers;
    private final RetrievalRankingService retrievalRankingService;

    public AiRetrieverFacade(List<ContextRetriever> retrievers) {
        this(retrievers, new RetrievalRankingService());
    }

    public AiRetrieverFacade(List<ContextRetriever> retrievers,
                             RetrievalRankingService retrievalRankingService) {
        this.retrievers = retrievers == null ? List.of() : List.copyOf(retrievers);
        this.retrievalRankingService = retrievalRankingService == null ? new RetrievalRankingService() : retrievalRankingService;
    }

    public List<RetrievalDocument> retrieve(AiExecutionContext context) {
        List<RetrievalDocument> merged = new ArrayList<>();
        for (ContextRetriever retriever : retrievers) {
            List<RetrievalDocument> documents = retriever.retrieve(context);
            if (documents != null && !documents.isEmpty()) {
                merged.addAll(documents);
            }
        }
        return retrievalRankingService.rank(context, merged);
    }
}
