package com.simone.blog.controller;

import com.simone.blog.dto.CreatePostDTO;
import com.simone.blog.dto.PostDTO;
import com.simone.blog.dto.UpdatePostDTO;
import com.simone.blog.service.PostService;
import com.simone.blog.validation.SortValidator;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    private static final Set<String> SORTABLE_FIELDS = Set.of("title", "createdAt", "category.name");

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public Page<PostDTO> getPosts(@RequestParam(name = "category", required = false) List<String> slugs, Pageable pageable){
        SortValidator.checkValidity(pageable,SORTABLE_FIELDS);
        return this.postService.getPosts(slugs,pageable);
    }

    @GetMapping("/{id}")
    public PostDTO getPostById(@PathVariable Long id){
        return this.postService.getPostById(id);
    }

    @PostMapping
    public PostDTO createPost(@RequestBody @Valid CreatePostDTO dto){
        return this.postService.createPost(dto);
    }

    @PutMapping("/{id}")
    public PostDTO updatePost(@PathVariable Long id,@RequestBody @Valid UpdatePostDTO dto){ return this.postService.updatePost(id,dto);}

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long id){this.postService.deletePost(id);}

}
