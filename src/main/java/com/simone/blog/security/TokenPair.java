package com.simone.blog.security;

public record TokenPair(String accessToken, String refreshToken) {
    @Override
    public String toString() {
        return "TokenPair{" +
                "accessToken='***'" +
                ", refreshToken='***'" +
                '}';
    }
}
