package com.citytrip.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveItineraryReqDTO {
    @NotNull(message = "sourceItineraryId must not be null")
    private Long sourceItineraryId;
    @Size(max = 64, message = "selectedOptionKey must be at most 64 characters")
    private String selectedOptionKey;
    @Size(max = 80, message = "title must be at most 80 characters")
    private String title;
}
