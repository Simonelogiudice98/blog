package com.simone.blog.service;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.dto.UpdatePostDTO;
import com.simone.blog.entity.Category;
import com.simone.blog.entity.Post;
import com.simone.blog.mapper.PostMapper;
import com.simone.blog.repository.CategoryRepository;
import com.simone.blog.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


@Service
public class PostService {
    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final PostMapper postMapper;

    public PostService(PostRepository postRepository, CategoryRepository categoryRepository, PostMapper postMapper) {

        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.postMapper = postMapper;
    }

    private Category resolveAssignableCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Categoria non trovata: " + categoryId));

        var isParent = categoryRepository.existsByParentId(categoryId);
        if (isParent) {
            throw new RuntimeException("Non è possibile assegnare un post a una macro-categoria: " + category.getName());
        }
        return category;

    }

    @Transactional(readOnly = true)
    public Page<PostDTO> getPosts(List<String> slugs, Pageable pageable) {

        Page<Post> posts;

        if (slugs != null && !slugs.isEmpty()) {
            List<Category> categories = categoryRepository.findBySlugIn(slugs);
            if (categories.isEmpty()) {
                throw new RuntimeException("Nessuna categoria trovata: " + slugs);
            }

            List<Long> requestedIds = categories.stream().map(Category::getId).toList();
            List<Category> children = categoryRepository.findByParentIdIn(requestedIds);

            Set<Long> ids = new LinkedHashSet<>();
            ids.addAll(requestedIds);
            ids.addAll(children
                    .stream()
                    .map(Category::getId)
                    .toList());

            posts = postRepository.findByCategoryIds(ids, pageable);

        } else {
            posts = postRepository.findAllWithCategory(pageable);
        }

        return posts.map(postMapper::toDto);

    }

    @Transactional(readOnly = true)
    public PostDTO getPostById(Long id) {

        var post = postRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new RuntimeException("Post non trovato: " + id));

        return postMapper.toDto(post);

    }

    @Transactional
    public PostDTO createPost(CreatePostDTO dto) {

        Category category = resolveAssignableCategory(dto.categoryId());

        Post newPost = postRepository.save(postMapper.toEntity(dto, category));
        return postMapper.toDto(newPost);

    }

    @Transactional
    public PostDTO updatePost(Long id, UpdatePostDTO dto) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post non trovato: " + id));

        Category category = resolveAssignableCategory(dto.categoryId());

        post.setTitle(dto.title());
        post.setContent(dto.content());
        post.setImageUrl(dto.imageUrl());
        post.setCategory(category);

        Post postUpdated = postRepository.save(post);
        return postMapper.toDto(postUpdated);
    }

    @Transactional
    public void deletePost(Long id){
        Post post = postRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Post non trovato: " + id));

        postRepository.delete(post);
    }

}
