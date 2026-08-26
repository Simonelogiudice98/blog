package com.simone.blog.dto;

import java.time.LocalDateTime;

public record PostDTO(Long id, String title, String content, String imageUrl, LocalDateTime createdAt, CategoryDTO category) {
}
