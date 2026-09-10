package com.simone.blog.dto;

import java.util.List;

public record CategoryTreeDTO(Long id, String name, String slug, List<CategoryDTO> children) {

}
