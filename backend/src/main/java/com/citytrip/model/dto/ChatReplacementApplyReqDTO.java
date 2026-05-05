package com.citytrip.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChatReplacementApplyReqDTO {
    @NotBlank(message = "clientSessionId must not be blank")
    @Size(max = 128, message = "clientSessionId must be at most 128 characters")
    private String clientSessionId;
    @NotBlank(message = "proposalToken must not be blank")
    @Size(max = 255, message = "proposalToken must be at most 255 characters")
    private String proposalToken;
    @Valid
    @Size(max = 80, message = "currentNodes must contain at most 80 items")
    private List<ChatReqDTO.ChatRouteNode> currentNodes;
    @Valid
    private GenerateReqDTO originalReq;
}
