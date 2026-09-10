package com.simone.blog.controller;

import com.simone.blog.dto.CategoryDTO;
import com.simone.blog.dto.CreateCategoryDTO;
import com.simone.blog.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public CategoryDTO createCategory(@RequestBody @Valid CreateCategoryDTO dto){return this.categoryService.createCategory(dto);}
}
