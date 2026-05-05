package com.citytrip.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PublicStatusReqDTO {
    private Boolean isPublic;
    @Size(max = 80, message = "title must be at most 80 characters")
    private String title;
    @Size(max = 500, message = "shareNote must be at most 500 characters")
    private String shareNote;
    @Size(max = 450000, message = "coverImageUrl must be at most 450000 characters")
    private String coverImageUrl;
    @Size(max = 64, message = "selectedOptionKey must be at most 64 characters")
    private String selectedOptionKey;
    @Size(max = 12, message = "themes must contain at most 12 items")
    private List<String> themes;
}
