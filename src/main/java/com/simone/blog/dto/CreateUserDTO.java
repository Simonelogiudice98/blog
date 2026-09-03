package com.simone.blog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserDTO(
        @NotBlank @Size(max = 255) @Email String email,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
