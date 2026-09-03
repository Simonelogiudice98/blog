package com.simone.blog.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final String secret;
    private final Long expiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,@Value("${jwt.expiration}") Long expiration) {
        this.secret = secret;
        this.expiration = expiration;
    }
}
