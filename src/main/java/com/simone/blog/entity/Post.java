package com.simone.blog.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column( nullable = false)
    private String title;

    @Column (nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column (name = "image_url")
    private String imageUrl;

    @Column (nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column (name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = null;
    };

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    };
}
