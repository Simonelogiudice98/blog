package com.simone.blog.controller;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public Page<PostDTO> getPosts(@RequestParam(name = "category", required = false) List<String> slugs, Pageable pageable){
        return this.postService.getPosts(slugs,pageable);
    }

    @GetMapping("/{id}")
    public PostDTO getPostById(@PathVariable Long id){
        return this.postService.getPostById(id);
    }

    @PostMapping
    public PostDTO createPost(@RequestBody CreatePostDTO dto){
        return this.postService.createPost(dto);
    }

}
