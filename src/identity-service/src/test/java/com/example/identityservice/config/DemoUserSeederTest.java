package com.example.identityservice.config;

import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoUserSeederTest {

    private static final String DEMO_PASSWORD = "Demo@12345";

    @Mock
    private UserRepository userRepository;

    // Low cost factor keeps the 13 hashes fast
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private DemoUserSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoUserSeeder(userRepository, passwordEncoder, DEMO_PASSWORD);
    }

    @Test
    @DisplayName("DEMO_USERS contains 10 drivers and 3 passengers with unique lower-case emails and no ADMIN")
    void demoUsers_list_hasExpectedShape() {
        List<DemoUserSeeder.DemoUser> users = DemoUserSeeder.DEMO_USERS;

        assertEquals(10, users.stream().filter(u -> u.role() == UserRole.DRIVER).count());
        assertEquals(3, users.stream().filter(u -> u.role() == UserRole.PASSENGER).count());
        assertTrue(users.stream().noneMatch(u -> u.role() == UserRole.ADMIN));
        Set<String> emails = users.stream().map(DemoUserSeeder.DemoUser::email).collect(Collectors.toSet());
        assertEquals(users.size(), emails.size());
        assertTrue(emails.stream().allMatch(e -> e.equals(e.toLowerCase())));
    }

    @Test
    @DisplayName("run on an empty database saves every demo user with a BCrypt hash of the demo password")
    void run_emptyDatabase_savesAllDemoUsers() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(DemoUserSeeder.DEMO_USERS.size())).saveAndFlush(captor.capture());
        List<User> saved = captor.getAllValues();
        assertEquals("driver01@ds03.test", saved.get(0).getEmail());
        assertEquals("Tran Van Binh", saved.get(0).getFullName());
        assertEquals(UserRole.DRIVER, saved.get(0).getRole());
        for (User user : saved) {
            assertNotEquals(DEMO_PASSWORD, user.getPasswordHash());
            assertTrue(passwordEncoder.matches(DEMO_PASSWORD, user.getPasswordHash()));
        }
    }

    @Test
    @DisplayName("run skips emails that already exist, so restarting the service creates no duplicates")
    void run_someUsersExist_onlySavesMissingOnes() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("driver01@ds03.test")).thenReturn(true);
        when(userRepository.existsByEmail("passenger01@ds03.test")).thenReturn(true);

        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(DemoUserSeeder.DEMO_USERS.size() - 2)).saveAndFlush(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .noneMatch(u -> u.getEmail().equals("driver01@ds03.test") || u.getEmail().equals("passenger01@ds03.test")));
    }

    @Test
    @DisplayName("run continues with the next user when another instance inserted the same email first")
    void run_concurrentInsertOfSameEmail_skipsAndContinues() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> seeder.run(null));

        verify(userRepository, times(DemoUserSeeder.DEMO_USERS.size())).saveAndFlush(any(User.class));
    }
}
