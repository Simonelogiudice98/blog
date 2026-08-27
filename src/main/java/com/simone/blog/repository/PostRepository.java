package com.simone.blog.repository;

import com.simone.blog.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.category c",countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findAllWithCategory(Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.category c WHERE p.id = :id")
    Optional<Post> findByIdWithCategory(@Param("id") Long id);

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.category c WHERE c.slug = :slug",countQuery = "SELECT COUNT(p) FROM Post p JOIN p.category c WHERE c.slug = :slug")
    Page<Post> findByCategorySlug(@Param("slug") String slug,Pageable pageable);

}
