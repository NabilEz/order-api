package com.nabil.order_api.security.service;


import com.nabil.order_api.exception.ConflictException;
import com.nabil.order_api.security.dto.AuthResponse;
import com.nabil.order_api.security.dto.LoginRequest;
import com.nabil.order_api.security.dto.RegisterRequest;
import com.nabil.order_api.security.entity.User;
import com.nabil.order_api.security.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email già registrata: " + req.email());
        }

        var role = req.role() != null && req.role().equalsIgnoreCase("ADMIN")
                ? User.Role.ADMIN : User.Role.USER;

        var user = new User(
                req.email(),
                passwordEncoder.encode(req.password()),
                role
        );
        userRepository.save(user);
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        var user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Credenziali non valide"));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BadCredentialsException("Credenziali non valide");
        }

        return buildResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        var rt = refreshTokenService.validate(refreshToken);
        refreshTokenService.revoke(refreshToken); // rotation — ogni refresh genera nuovo token
        return buildResponse(rt.getUser());
    }

    private AuthResponse buildResponse(User user) {
        var accessToken  = jwtService.generate(user.getEmail(), user.getRole().name());
        var refreshToken = refreshTokenService.create(user);
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationSeconds()
        );
    }
}