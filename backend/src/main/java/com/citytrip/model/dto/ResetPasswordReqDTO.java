package com.citytrip.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordReqDTO {
    @NotBlank(message = "email must not be blank")
    @Email(message = "email format is invalid")
    @Size(max = 128, message = "email must be at most 128 characters")
    private String email;

    @NotBlank(message = "email code must not be blank")
    @Size(min = 6, max = 6, message = "email code must be 6 characters")
    private String emailCode;

    @NotBlank(message = "password must not be blank")
    @Size(min = 6, max = 128, message = "password must be 6 to 128 characters")
    private String password;
}
