package com.simone.blog.controller;

import com.simone.blog.dto.CreateUserDTO;
import com.simone.blog.dto.LoginRequestDTO;
import com.simone.blog.dto.LoginResponseDTO;
import com.simone.blog.dto.UserDTO;
import com.simone.blog.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authservice) {
        this.authService = authservice;
    }

    @PostMapping("/register")
    public UserDTO createUser(@RequestBody @Valid CreateUserDTO dto){return this.authService.register(dto);}

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody @Valid LoginRequestDTO dto){return this.authService.login(dto);}
}
