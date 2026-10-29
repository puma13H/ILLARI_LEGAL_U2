package com.casoslegales.service;

import com.casoslegales.dto.AuthResponse;
import com.casoslegales.dto.LoginRequest;
import com.casoslegales.model.User;
import com.casoslegales.repository.UserRepository;
import com.casoslegales.security.CustomUserDetailsService;
import com.casoslegales.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       CustomUserDetailsService userDetailsService,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (request.role() != null && !request.role().isBlank()
                && !user.getRole().getName().equalsIgnoreCase(request.role())) {
            throw new RuntimeException("Rol no autorizado para este acceso");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtService.generateToken(userDetails, user.getRole().getName());

        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().getName());
    }
}
