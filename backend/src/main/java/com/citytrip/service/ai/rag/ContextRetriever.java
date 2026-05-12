package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;

import java.util.List;

@FunctionalInterface
public interface ContextRetriever {

    List<RetrievalDocument> retrieve(AiExecutionContext context);
}
