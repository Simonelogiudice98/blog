package com.simone.blog.controller;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.service.PostService;
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
    public List<PostDTO> getPosts(){
        return this.postService.getPosts();
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
