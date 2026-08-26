package com.simone.blog.service;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.mapper.PostMapper;
import com.simone.blog.repository.CategoryRepository;
import com.simone.blog.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final PostMapper postMapper;

    public PostService(PostRepository postRepository,CategoryRepository categoryRepository, PostMapper postMapper) {

        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.postMapper = postMapper;
    }

    public List<PostDTO> getPosts() {

        return postRepository.findAll()
                .stream()
                .map(postMapper::toDto)
                .toList();
    }

    public PostDTO getPostById(Long id) {

        var post = postRepository.findById(id);

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

        var newPost = postRepository.save(postMapper.toEntity(dto,category.get()));
        return postMapper.toDto(newPost);

    }
}
