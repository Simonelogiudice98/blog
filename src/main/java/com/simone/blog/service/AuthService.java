package com.simone.blog.service;

import com.simone.blog.dto.CreateUserDTO;
import com.simone.blog.dto.LoginRequestDTO;
import com.simone.blog.dto.UserDTO;
import com.simone.blog.entity.Role;
import com.simone.blog.entity.User;
import com.simone.blog.exception.BadRequestException;
import com.simone.blog.exception.UnauthorizedException;
import com.simone.blog.mapper.UserMapper;
import com.simone.blog.repository.UserRepository;
import com.simone.blog.security.JwtTokenProvider;
import com.simone.blog.security.TokenPair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final String hashBait;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper, JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.hashBait = passwordEncoder.encode("stringaAcaso");
    }

    @Transactional
    public UserDTO register(CreateUserDTO dto) {
        boolean emailAlreadyExists = userRepository.existsByEmail(dto.email());
        if (emailAlreadyExists) {
            throw new BadRequestException("Email già in uso");
        }

        boolean usernameAlreadyExists = userRepository.existsByUsername(dto.username());
        if (usernameAlreadyExists) {
            throw new BadRequestException("Username già in uso");
        }

        String encodedPassword = passwordEncoder.encode(dto.password());
        User newUser = userRepository.save(userMapper.toEntity(dto, encodedPassword, Role.USER));
        return userMapper.toDto(newUser);

    }

    @Transactional
    public TokenPair login(LoginRequestDTO dto) {

        Optional<User> found = userRepository.findByEmail(dto.email());

        if(found.isEmpty()){
            passwordEncoder.matches(dto.password(),hashBait);
            throw new UnauthorizedException("Email o Password errati");
        }

        User user = found.get();

        if(!passwordEncoder.matches(dto.password(),user.getPassword())){
            throw new UnauthorizedException("Email o Password errati");
        }

        String refreshToken = refreshTokenService.createSession(user);
        String accessToken = jwtTokenProvider.generateToken(user);

        return new TokenPair(accessToken,refreshToken);

    }
}
