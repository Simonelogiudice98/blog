package com.simone.blog.controller;

import com.simone.blog.dto.CategoryDTO;
import com.simone.blog.dto.CategoryTreeDTO;
import com.simone.blog.dto.CreateCategoryDTO;
import com.simone.blog.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public CategoryDTO createCategory(@RequestBody @Valid CreateCategoryDTO dto){return this.categoryService.createCategory(dto);}

    @GetMapping
    public List<CategoryTreeDTO> getCategoriesTree(){return this.categoryService.getCategoryTree();}
}
