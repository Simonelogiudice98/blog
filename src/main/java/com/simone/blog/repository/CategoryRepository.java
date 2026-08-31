package com.simone.blog.repository;

import com.simone.blog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findBySlugIn(List<String> slugs);

    List<Category> findByParentIdIn(List<Long> parentIds);

    boolean existsByParentId(Long parentId);
}
