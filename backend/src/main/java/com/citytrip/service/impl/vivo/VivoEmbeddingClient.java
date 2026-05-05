package com.citytrip.service.impl.vivo;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VivoEmbeddingClient {

    public List<List<Double>> embed(String modelName, List<String> sentences) {
        return List.of();
    }

    public boolean isAvailable() {
        return false;
    }
}
