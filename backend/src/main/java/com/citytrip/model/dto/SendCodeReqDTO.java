package com.citytrip.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendCodeReqDTO {
    @NotBlank(message = "email must not be blank")
    @Email(message = "email format is invalid")
    @Size(max = 128, message = "email must be at most 128 characters")
    private String email;
}
