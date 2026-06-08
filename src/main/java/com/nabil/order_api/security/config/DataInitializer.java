package com.nabil.order_api.security.config;


import com.nabil.order_api.security.entity.User;
import com.nabil.order_api.security.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    // Gira sempre — in prod sostituisci con @Profile("dev") se vuoi
    @Bean
    CommandLineRunner seedUsers(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (!repo.existsByEmail("admin@demo.com")) {
                repo.save(new User("admin@demo.com",
                        encoder.encode("Admin1234!"), User.Role.ADMIN));
            }
            if (!repo.existsByEmail("user@demo.com")) {
                repo.save(new User("user@demo.com",
                        encoder.encode("User1234!"), User.Role.USER));
            }
        };
    }
}