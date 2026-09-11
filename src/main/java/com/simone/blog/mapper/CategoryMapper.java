package com.simone.blog.mapper;


import com.simone.blog.dto.CategoryDTO;
import com.simone.blog.dto.CategoryTreeDTO;
import com.simone.blog.dto.CreateCategoryDTO;
import com.simone.blog.entity.Category;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class CategoryMapper {

    public Category toEntity(CreateCategoryDTO dto, String slug, Category parent){
        Category category = new Category();
        category.setName(dto.name());
        category.setSlug(slug);
        category.setParent(parent);

        return category;
    }

    public CategoryDTO toDto(Category category){

        return new CategoryDTO(category.getId(),category.getName(),category.getSlug());
    }

    public CategoryTreeDTO toCategoryTreeDto(Category macro, List<Category> children){
        List<CategoryDTO> childrenDto = children.stream().map(this::toDto).toList();
        return new CategoryTreeDTO(macro.getId(),macro.getName(),macro.getSlug(),childrenDto);
    }

}
