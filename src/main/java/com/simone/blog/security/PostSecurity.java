package com.simone.blog.security;

import com.simone.blog.repository.PostRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PostSecurity {

    private final PostRepository postRepository;

    public PostSecurity(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public boolean isAuthor(Long postId){

        if(postRepository.existsById(postId)){
            JwtPrincipal principal = (JwtPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            return postRepository.existsByIdAndAuthorEmail(postId, principal.email());

        }else{
            return true;
        }
    }
}
