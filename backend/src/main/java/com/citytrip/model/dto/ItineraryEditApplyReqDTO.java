package com.citytrip.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ItineraryEditApplyReqDTO {
    @Size(max = 32, message = "source must be at most 32 characters")
    private String source;
    @Size(max = 255, message = "summary must be at most 255 characters")
    private String summary;
    @Valid
    @Size(max = 80, message = "operations must contain at most 80 items")
    private List<ItineraryEditOperationDTO> operations = new ArrayList<>();
}
