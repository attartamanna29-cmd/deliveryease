package com.deliverease.controller;

import com.deliverease.dto.Dtos.*;
import com.deliverease.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @GetMapping("/profile")
    public ProfileResponse profile(Authentication a) { return auth.profile(a.getName()); }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(Authentication a, @Valid @RequestBody ProfileUpdate p) {
        return auth.updateProfile(a.getName(), p);
    }
}
