package com.deliverease.service;

import com.deliverease.dto.Dtos.*;
import com.deliverease.exception.AppException;
import com.deliverease.model.Role;
import com.deliverease.model.User;
import com.deliverease.repository.UserRepository;
import com.deliverease.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtUtil jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "An account with this email already exists");
        }
        User u = new User();
        u.setName(r.name().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(r.password()));
        u.setPhone(r.phone());
        u.setAddress(r.address());
        u.setRole(Role.CUSTOMER);          // public registration always creates a customer
        users.save(u);
        return response(u);
    }

    public AuthResponse login(LoginRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase())
                .filter(x -> encoder.matches(r.password(), x.getPassword()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return response(u);
    }

    public ProfileResponse profile(String email) {
        return toProfile(find(email));
    }

    public ProfileResponse updateProfile(String email, ProfileUpdate p) {
        User u = find(email);
        u.setName(p.name().trim());
        u.setPhone(p.phone());
        u.setAddress(p.address());
        return toProfile(users.save(u));
    }

    private User find(String email) {
        return users.findByEmail(email).orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private AuthResponse response(User u) {
        return new AuthResponse(jwt.generateToken(u.getEmail(), u.getRole().name()), u.getName(), u.getEmail(), u.getRole().name());
    }

    private ProfileResponse toProfile(User u) {
        return new ProfileResponse(u.getName(), u.getEmail(), u.getPhone(), u.getAddress(), u.getRole().name());
    }
}
