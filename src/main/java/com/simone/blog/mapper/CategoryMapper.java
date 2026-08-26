package com.simone.blog.mapper;


import com.simone.blog.dto.CategoryDTO;
import com.simone.blog.entity.Category;
import org.springframework.stereotype.Component;


@Component
public class CategoryMapper {

    public CategoryDTO toDto(Category category){

        return new CategoryDTO(category.getId(),category.getName(),category.getSlug());
    }

}
