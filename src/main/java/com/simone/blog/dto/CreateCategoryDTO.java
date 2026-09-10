package com.simone.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryDTO(
        @NotBlank @Size(max = 60) String name,
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "solo lettere minuscole, cifre e trattini singoli") @Size(max = 60) String slug,
        Long parentId
) {
}
