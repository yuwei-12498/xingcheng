package com.citytrip.service.domain.scoring;

import com.citytrip.config.AlgorithmWeightsProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicAlgorithmWeightProviderTest {

    @Test
    void exposesDefaultsAndAllowsRuntimeSnapshotUpdate() {
        AlgorithmWeightsProperties properties = new AlgorithmWeightsProperties();
        DynamicAlgorithmWeightProvider provider = new DynamicAlgorithmWeightProvider(properties);

        assertThat(provider.current().companionMatchScore()).isEqualTo(2.5D);
        assertThat(provider.current().rainFriendlyScore()).isEqualTo(1.5D);

        AlgorithmWeightsSnapshot updated = provider.current().withCompanionMatchScore(9.0D)
                .withRainFriendlyScore(4.0D);
        provider.update(updated);

        assertThat(provider.current().companionMatchScore()).isEqualTo(9.0D);
        assertThat(provider.current().rainFriendlyScore()).isEqualTo(4.0D);
    }
}