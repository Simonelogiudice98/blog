package com.simone.blog.repository;

import com.simone.blog.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("DELETE FROM RefreshToken t WHERE t.familyId = :familyId ")
    @Modifying
    int deleteByFamilyId(@Param("familyId") UUID familyId);
}
