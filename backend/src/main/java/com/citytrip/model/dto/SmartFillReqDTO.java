package com.citytrip.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SmartFillReqDTO {
    @NotBlank(message = "smart-fill text must not be blank")
    @Size(max = 1000, message = "smart-fill text must be at most 1000 characters")
    private String text;
}
