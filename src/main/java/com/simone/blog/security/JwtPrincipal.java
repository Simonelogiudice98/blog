package com.simone.blog.security;

public record JwtPrincipal(String email, String role) {
}
