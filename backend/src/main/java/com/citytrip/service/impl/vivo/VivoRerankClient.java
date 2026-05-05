package com.citytrip.service.impl.vivo;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VivoRerankClient {

    public List<Double> rerank(String query, List<String> sentences) {
        return List.of();
    }

    public boolean isAvailable() {
        return false;
    }
}
