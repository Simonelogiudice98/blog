package com.simone.blog.mapper;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.entity.Category;
import com.simone.blog.entity.Post;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {

    private final CategoryMapper categoryMapper;

    public PostMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public Post toEntity(CreatePostDTO dto,Category category){
        Post post = new Post();
        post.setTitle(dto.title());
        post.setContent(dto.content());
        post.setImageUrl(dto.imageUrl());
        post.setCategory(category);
        return post;
    }

    public PostDTO toDto(Post post){
        return new PostDTO(post.getId(),post.getTitle(),post.getContent(), post.getImageUrl(), post.getCreatedAt(),categoryMapper.toDto(post.getCategory()));

    }

}
