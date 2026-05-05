package com.citytrip.service.domain.scoring;

import com.citytrip.config.AlgorithmWeightsProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DynamicAlgorithmWeightProvider implements AlgorithmWeightProvider {

    private final AlgorithmWeightsSnapshot defaults;
    private final AtomicReference<AlgorithmWeightsSnapshot> current;

    public DynamicAlgorithmWeightProvider(AlgorithmWeightsProperties properties) {
        this.defaults = AlgorithmWeightsSnapshot.fromProperties(properties);
        this.current = new AtomicReference<>(defaults);
    }

    @Override
    public AlgorithmWeightsSnapshot current() {
        return current.get();
    }

    public void update(AlgorithmWeightsSnapshot snapshot) {
        current.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public void resetToDefaults() {
        current.set(defaults);
    }
}