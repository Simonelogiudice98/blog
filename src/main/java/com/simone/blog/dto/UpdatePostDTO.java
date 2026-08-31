package com.simone.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdatePostDTO(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @URL(protocol = "https") @Size(max = 255) String imageUrl,
        @NotNull Long categoryId) {
}
