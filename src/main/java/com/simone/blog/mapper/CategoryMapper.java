package com.simone.blog.mapper;


import com.simone.blog.dto.CategoryDTO;
import com.simone.blog.dto.CreateCategoryDTO;
import com.simone.blog.entity.Category;
import org.springframework.stereotype.Component;


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

}
