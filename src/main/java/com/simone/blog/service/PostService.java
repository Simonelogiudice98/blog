package com.simone.blog.service;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.entity.Category;
import com.simone.blog.entity.Post;
import com.simone.blog.mapper.PostMapper;
import com.simone.blog.repository.CategoryRepository;
import com.simone.blog.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


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

    public Page<PostDTO> getPosts(String slug, Pageable pageable) {

        Page<Post> posts;

        if (slug != null) {
            Category category = categoryRepository.findBySlug(slug)
                    .orElseThrow(() -> new RuntimeException("Categoria non trovata: " + slug));

            List<Category> children = categoryRepository.findByParentId(category.getId());

            List<Long> ids = new ArrayList<>();
            ids.add(category.getId());
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

    public PostDTO getPostById(Long id) {

        var post = postRepository.findByIdWithCategory(id);

        if (post.isEmpty()) {
            throw new RuntimeException();
        }
        return postMapper.toDto(post.get());

    }

    public PostDTO createPost(CreatePostDTO dto) {

        var category = categoryRepository.findById(dto.categoryId());
        if (category.isEmpty()) {
            throw new RuntimeException();

        }

        var newPost = postRepository.save(postMapper.toEntity(dto, category.get()));
        return postMapper.toDto(newPost);

    }

}
