package com.citytrip.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommunityCommentReqDTO {
    private Long parentId;
    @NotBlank(message = "comment content must not be blank")
    @Size(max = 1000, message = "comment content must be at most 1000 characters")
    private String content;
}
