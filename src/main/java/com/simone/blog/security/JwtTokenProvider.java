package com.simone.blog.security;

import com.simone.blog.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expiration;
    private final JwtParser parser;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
        this.expiration = expiration;
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.parser = Jwts.parser().verifyWith(secretKey).build();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(secretKey)
                .compact();
    }

    public Optional<JwtPrincipal> extractPrincipal(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return Optional.of(new JwtPrincipal(claims.getSubject(),claims.get("role",String.class)));
        } catch (JwtException e) {
            log.debug("Token non valido: {}", e.getMessage());
            return Optional.empty();
        }
    }

}
