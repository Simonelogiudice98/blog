package com.simone.blog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        indexes = @Index(
               name = "idx_refresh_token_family_id",
                columnList = "family_id"
        )
)
@NoArgsConstructor
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column( nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User user;

    @Column(name = "family_id",nullable = false)
    private UUID familyId;

    @Column( nullable = false)
    private Instant createdAt;


    private Instant usedAt;

    @Column( nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = Instant.now();
    }
}
