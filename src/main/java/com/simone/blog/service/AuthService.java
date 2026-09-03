package com.simone.blog.service;

import com.simone.blog.dto.CreateUserDTO;
import com.simone.blog.dto.UserDTO;
import com.simone.blog.entity.Role;
import com.simone.blog.entity.User;
import com.simone.blog.exception.BadRequestException;
import com.simone.blog.mapper.UserMapper;
import com.simone.blog.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserDTO register(CreateUserDTO dto){
        boolean emailAlreadyExists = userRepository.existsByEmail(dto.email());
        if(emailAlreadyExists){
             throw new BadRequestException("Email già in uso");
        }

        boolean usernameAlreadyExists = userRepository.existsByUsername(dto.username());
        if(usernameAlreadyExists){
            throw new BadRequestException("Username già in uso");
        }

        String encodedPassword = passwordEncoder.encode(dto.password());
        User newUser = userRepository.save(userMapper.toEntity(dto,encodedPassword,Role.USER));
        return userMapper.toDto(newUser);

    }
}
