package com.example.identityservice.config;

import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates fake DRIVER and PASSENGER accounts at startup so the demo (Register -> Login -> Trip) works on an empty
 * identity_db. All accounts share the password from app.seed.password. Emails that already exist are left untouched,
 * so restarting the service is safe. Turn it off with SEED_DATA_ENABLED=false.
 * Account list: docs/test-data/identity/README.md
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("Tran Van Binh", "driver01@ds03.test", UserRole.DRIVER),
            new DemoUser("Le Hoang Nam", "driver02@ds03.test", UserRole.DRIVER),
            new DemoUser("Pham Minh Tuan", "driver03@ds03.test", UserRole.DRIVER),
            new DemoUser("Hoang Duc Anh", "driver04@ds03.test", UserRole.DRIVER),
            new DemoUser("Vu Thi Lan", "driver05@ds03.test", UserRole.DRIVER),
            new DemoUser("Do Quang Huy", "driver06@ds03.test", UserRole.DRIVER),
            new DemoUser("Bui Van Long", "driver07@ds03.test", UserRole.DRIVER),
            new DemoUser("Dang Thi Mai", "driver08@ds03.test", UserRole.DRIVER),
            new DemoUser("Ngo Thanh Son", "driver09@ds03.test", UserRole.DRIVER),
            new DemoUser("Duong Van Khoa", "driver10@ds03.test", UserRole.DRIVER),
            new DemoUser("Nguyen Thi Hoa", "passenger01@ds03.test", UserRole.PASSENGER),
            new DemoUser("Ly Minh Chau", "passenger02@ds03.test", UserRole.PASSENGER),
            new DemoUser("Phan Gia Bao", "passenger03@ds03.test", UserRole.PASSENGER)
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoUserSeeder(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.seed.password}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        for (DemoUser demo : DEMO_USERS) {
            if (userRepository.existsByEmail(demo.email())) {
                continue;
            }
            try {
                userRepository.saveAndFlush(User.builder()
                        .email(demo.email())
                        .passwordHash(passwordEncoder.encode(demoPassword))
                        .fullName(demo.fullName())
                        .role(demo.role())
                        .build());
                created++;
            } catch (DataIntegrityViolationException ex) {
                // Another identity-service instance inserted the same email between the check and the insert
                log.info("Demo user {} was created by another instance, skipping", demo.email());
            }
        }
        log.info("Demo user seeding finished: created={}, alreadyPresent={}", created, DEMO_USERS.size() - created);
    }

    record DemoUser(String fullName, String email, UserRole role) {
    }
}
