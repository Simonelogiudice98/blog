package com.simone.blog.service;

import com.simone.blog.dto.CategoryDTO;
import com.simone.blog.dto.CategoryTreeDTO;
import com.simone.blog.dto.CreateCategoryDTO;
import com.simone.blog.entity.Category;
import com.simone.blog.exception.BadRequestException;
import com.simone.blog.mapper.CategoryMapper;
import com.simone.blog.repository.CategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public CategoryDTO createCategory(CreateCategoryDTO dto) {
        Category parent = null;

        if (dto.parentId() != null) {
            parent = categoryRepository.findById(dto.parentId())
                    .orElseThrow(() -> new BadRequestException("Nessuna categoria trovata per l'id: " + dto.parentId()));

            if (parent.getParent() != null) {
                throw new BadRequestException("La categoria genitore deve essere una macro-categoria: " + parent.getName() + " è già una sottocategoria");
            }

        }

        String slug;
        if (dto.slug() == null) {
            slug = generateSlug(dto.name());
        } else {
            slug = dto.slug();
        }

        if (slug.isEmpty()) {
            throw new BadRequestException("Impossibile generare uno slug dal nome: " + dto.name() + ". Specificalo esplicitamente");
        }

        boolean nameAlreadyExist = categoryRepository.existsByNameIgnoreCase(dto.name());
        if (nameAlreadyExist) {
            throw new BadRequestException("Nome già in uso: " + dto.name());
        }

        boolean slugAlreadyExist = categoryRepository.existsBySlug(slug);
        if (slugAlreadyExist) {
            throw new BadRequestException("Slug già in uso: " + slug);
        }


        Category newCategory = categoryRepository.save(categoryMapper.toEntity(dto, slug, parent));
        return categoryMapper.toDto(newCategory);

    }

    private String generateSlug(String name) {
        return Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

    }

    @Transactional(readOnly = true)
    public List<CategoryTreeDTO> getCategoryTree() {
        List<Category> categories = categoryRepository.findAll(Sort.by("name"));
        List<Category> macros = categories.stream().filter(c -> c.getParent() == null).toList();
        Map<Long, List<Category>> childrenByParentId = categories.stream().filter(c -> c.getParent() != null).collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return macros.stream().map(m -> categoryMapper.toCategoryTreeDto(m, childrenByParentId.getOrDefault(m.getId(), List.of()))).toList();

    }

}
