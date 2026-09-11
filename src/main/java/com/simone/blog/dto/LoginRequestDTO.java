package com.simone.blog.dto;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;

public record LoginRequestDTO(@NotBlank String email, @NotBlank String password) {

    @Override
    public @NonNull String toString() {
        return "LoginRequestDTO[email=" + email + ", password=***]";
    }
}
