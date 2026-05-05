package com.citytrip.model.dto;

import com.citytrip.model.vo.ItineraryNodeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReplanReqDTO {
    @Size(max = 80, message = "currentNodes must contain at most 80 items")
    private List<ItineraryNodeVO> currentNodes;
    @Valid
    private GenerateReqDTO originalReq;
    @Size(max = 80, message = "excludedSignatures must contain at most 80 items")
    private List<String> excludedSignatures;
}
