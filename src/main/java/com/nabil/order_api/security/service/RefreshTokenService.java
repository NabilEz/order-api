package com.nabil.order_api.security.service;



import com.nabil.order_api.exception.BadRequestException;
import com.nabil.order_api.security.entity.RefreshToken;
import com.nabil.order_api.security.entity.User;
import com.nabil.order_api.security.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RefreshToken create(User user) {
        // revoca i precedenti prima di crearne uno nuovo
        repository.deleteByUserId(user.getId());

        var token = new RefreshToken(
                UUID.randomUUID().toString(),
                user,
                LocalDateTime.now().plusDays(7) // 7 giorni
        );
        return repository.save(token);
    }

    @Transactional
    public RefreshToken validate(String token) {
        return repository.findByToken(token)
                .filter(RefreshToken::isValid)
                .orElseThrow(() -> new BadRequestException("Refresh token non valido o scaduto"));
    }

    @Transactional
    public void revoke(String token) {
        repository.findByToken(token).ifPresent(rt -> {
            rt.revoke();
            repository.save(rt);
        });
    }
}
