package com.simone.blog.service;

import com.simone.blog.entity.RefreshToken;
import com.simone.blog.entity.User;
import com.simone.blog.exception.UnauthorizedException;
import com.simone.blog.repository.RefreshTokenRepository;
import com.simone.blog.security.JwtTokenProvider;
import com.simone.blog.security.TokenPair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshExpiration;
    private static final String INVALID_REFRESH_TOKEN = "Autenticazione richiesta";

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, @Value("${jwt.refresh-expiration}") Duration refreshExpiration,JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpiration = refreshExpiration;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    private String generateToken() {
        byte[] byteArray = new byte[32];
        secureRandom.nextBytes(byteArray);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteArray);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);

            byte[] hash = digest.digest(tokenBytes);

            return HexFormat.of().formatHex(hash);


        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile nella JVM", e);
        }
    }

    @Transactional
    public String createSession(User user){
       String token = generateToken();

        RefreshToken newRefreshToken = buildRefreshToken(token,user,UUID.randomUUID(),Instant.now().plus(refreshExpiration));
        refreshTokenRepository.save(newRefreshToken);
        return token;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenPair refresh(String token){
        String tokenHash = hash(token);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));

        if(refreshToken.getUsedAt() != null){
            refreshTokenRepository.deleteByFamilyId(refreshToken.getFamilyId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        if(refreshToken.getExpiresAt().isBefore(Instant.now())){
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        refreshToken.setUsedAt(Instant.now());
        String newToken = generateToken();

        RefreshToken newRefreshToken = buildRefreshToken(newToken,refreshToken.getUser(),refreshToken.getFamilyId(),refreshToken.getExpiresAt());
        refreshTokenRepository.save(newRefreshToken);

        String accessToken = jwtTokenProvider.generateToken(refreshToken.getUser());

        return new TokenPair(accessToken,newToken);

    }

    @Transactional
    public void logout(String token){
        refreshTokenRepository.findByTokenHash(hash(token))
                    .ifPresent((storedToken) -> refreshTokenRepository.deleteByFamilyId(storedToken.getFamilyId()));
    }

    private RefreshToken buildRefreshToken(String plainToken, User user,UUID familyId,Instant expiresAt){
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setTokenHash(hash(plainToken));
        newRefreshToken.setUser(user);
        newRefreshToken.setFamilyId(familyId);
        newRefreshToken.setExpiresAt(expiresAt);

        return newRefreshToken;
    }
}
