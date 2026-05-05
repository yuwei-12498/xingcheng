package com.citytrip.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginReqDTO {
    @NotBlank(message = "username must not be blank")
    @Size(max = 64, message = "username must be at most 64 characters")
    private String username;
    @NotBlank(message = "password must not be blank")
    @Size(max = 128, message = "password must be at most 128 characters")
    private String password;
}
