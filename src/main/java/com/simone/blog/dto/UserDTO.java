package com.simone.blog.dto;

import com.simone.blog.entity.Role;

public record UserDTO(Long id, String email, String fullName, String username, Role role) {
}
