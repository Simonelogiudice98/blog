package com.simone.blog.mapper;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.entity.Post;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {


    public Post toEntity(CreatePostDTO dto){
        Post post = new Post();
        post.setTitle(dto.title());
        post.setContent(dto.content());
        post.setImageUrl(dto.imageUrl());

        return post;
    }

    public PostDTO toDto(Post post){
        return new PostDTO(post.getId(),post.getTitle(),post.getContent(), post.getImageUrl(), post.getCreatedAt());

    }

}
