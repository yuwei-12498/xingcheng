package com.citytrip.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ItineraryEditRestoreReqDTO {
    @NotNull(message = "versionId must not be null")
    private Long versionId;
}
