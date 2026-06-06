package com.unique.examine.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginBody {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
