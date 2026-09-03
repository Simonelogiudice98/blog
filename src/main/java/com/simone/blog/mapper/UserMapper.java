package com.simone.blog.mapper;

import com.simone.blog.dto.CreateUserDTO;
import com.simone.blog.dto.UserDTO;
import com.simone.blog.entity.Role;
import com.simone.blog.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(CreateUserDTO dto,String hashedPassword, Role role){
        User user = new User();
        user.setEmail(dto.email());
        user.setPassword(hashedPassword);
        user.setUsername(dto.username());
        user.setFullName(dto.fullName());
        user.setRole(role);
        return user;
    }

    public UserDTO toDto(User user){
        return new UserDTO(user.getId(),user.getEmail(),user.getFullName(),user.getUsername(),user.getRole());
    }
}
