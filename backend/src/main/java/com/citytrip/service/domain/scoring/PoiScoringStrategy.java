package com.citytrip.service.domain.scoring;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;

public interface PoiScoringStrategy {
    ScoreBreakdown score(GenerateReqDTO request, Poi poi);
}