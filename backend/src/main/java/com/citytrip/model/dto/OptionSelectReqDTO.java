package com.citytrip.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OptionSelectReqDTO {
    @NotBlank(message = "selectedOptionKey must not be blank")
    @Size(max = 64, message = "selectedOptionKey must be at most 64 characters")
    private String selectedOptionKey;
    @Size(max = 64, message = "sourcePage must be at most 64 characters")
    private String sourcePage;
}
