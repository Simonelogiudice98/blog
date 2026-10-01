package com.simone.blog.controller;

import com.simone.blog.dto.CreateUserDTO;
import com.simone.blog.dto.LoginRequestDTO;
import com.simone.blog.dto.LoginResponseDTO;
import com.simone.blog.dto.UserDTO;
import com.simone.blog.exception.UnauthorizedException;
import com.simone.blog.security.TokenPair;
import com.simone.blog.service.AuthService;
import com.simone.blog.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final Duration refreshExpiration;
    private final static String REFRESH_COOKIE = "refreshToken";
    private final static String PATH = "/auth";
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService,@Value("${jwt.refresh-expiration}") Duration refreshExpiration, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshExpiration = refreshExpiration;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public UserDTO createUser(@RequestBody @Valid CreateUserDTO dto){return this.authService.register(dto);}

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto){

        TokenPair tokenPair = this.authService.login(dto);

        ResponseCookie cookie = buildCookie(tokenPair.refreshToken(),refreshExpiration);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponseDTO(tokenPair.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken){
        if(refreshToken == null){
            throw new UnauthorizedException("Autenticazione richiesta");
        }

        TokenPair tokenPair = refreshTokenService.refresh(refreshToken);

        ResponseCookie cookie = buildCookie(tokenPair.refreshToken(),refreshExpiration);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponseDTO(tokenPair.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken){
        if(refreshToken != null){
            refreshTokenService.logout(refreshToken);
        }

        ResponseCookie cookie = buildCookie("",Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString()).build();

    }

    private ResponseCookie buildCookie(String refreshToken,Duration maxAge){

        return ResponseCookie.from(REFRESH_COOKIE,refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();

    }
}
