package com.citytrip.model.dto;

import com.citytrip.model.vo.ItineraryVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatReplacementRestoreReqDTO {
    @NotBlank(message = "versionToken must not be blank")
    @Size(max = 255, message = "versionToken must be at most 255 characters")
    private String versionToken;
    private ItineraryVO itinerarySnapshot;
}
