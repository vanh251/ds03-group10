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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoUserSeederTest {

    private static final String DEMO_PASSWORD = "Demo@12345";

    @Mock
    private UserRepository userRepository;

    @Mock
    private NamedParameterJdbcOperations jdbc;

    // Low cost factor keeps the 13 hashes fast
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private DemoUserSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoUserSeeder(userRepository, jdbc, passwordEncoder, DEMO_PASSWORD);
    }

    @Test
    @DisplayName("DEMO_USERS has 10 drivers and 3 passengers with unique fixed ids and lower-case emails, no ADMIN")
    void demoUsers_list_hasExpectedShape() {
        List<DemoUserSeeder.DemoUser> users = DemoUserSeeder.DEMO_USERS;

        assertEquals(10, users.stream().filter(u -> u.role() == UserRole.DRIVER).count());
        assertEquals(3, users.stream().filter(u -> u.role() == UserRole.PASSENGER).count());
        assertTrue(users.stream().noneMatch(u -> u.role() == UserRole.ADMIN));
        Set<String> emails = users.stream().map(DemoUserSeeder.DemoUser::email).collect(Collectors.toSet());
        assertEquals(users.size(), emails.size());
        assertTrue(emails.stream().allMatch(e -> e.equals(e.toLowerCase())));
        assertEquals(users.size(), users.stream().map(DemoUserSeeder.DemoUser::id).distinct().count());
    }

    @Test
    @DisplayName("driver ids follow d0000000-0000-4000-8000-0000000000NN, the ids driver-service seeds its profiles with")
    void driverIds_followSharedPattern() {
        assertEquals(UUID.fromString("d0000000-0000-4000-8000-000000000001"), DemoUserSeeder.driverId(1));
        assertEquals(UUID.fromString("d0000000-0000-4000-8000-000000000010"), DemoUserSeeder.driverId(10));
        assertEquals(DemoUserSeeder.driverId(1), DemoUserSeeder.DEMO_USERS.get(0).id());
        assertEquals("driver01@ds03.test", DemoUserSeeder.DEMO_USERS.get(0).email());
        assertEquals(UUID.fromString("a0000000-0000-4000-8000-000000000003"), DemoUserSeeder.passengerId(3));
    }

    @Test
    @DisplayName("run on an empty database inserts every demo user with its fixed id and a BCrypt hash of the demo password")
    @SuppressWarnings("unchecked")
    void run_emptyDatabase_insertsAllWithFixedIds() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        seeder.run(null);

        ArgumentCaptor<Map<String, ?>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc, times(DemoUserSeeder.DEMO_USERS.size())).update(anyString(), params.capture());
        Map<String, ?> first = params.getAllValues().get(0);
        assertEquals(DemoUserSeeder.driverId(1), first.get("userId"));
        assertEquals("driver01@ds03.test", first.get("email"));
        assertEquals("Tran Van Binh", first.get("fullName"));
        assertEquals("DRIVER", first.get("role"));
        for (Map<String, ?> p : params.getAllValues()) {
            String hash = (String) p.get("passwordHash");
            assertNotEquals(DEMO_PASSWORD, hash);
            assertTrue(passwordEncoder.matches(DEMO_PASSWORD, hash));
        }
    }

    @Test
    @DisplayName("run skips emails that already exist, so restarting the service inserts nothing twice")
    void run_existingUsers_onlyInsertsMissingOnes() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("driver01@ds03.test"))
                .thenReturn(Optional.of(User.builder().userId(DemoUserSeeder.driverId(1)).build()));
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        seeder.run(null);

        verify(jdbc, times(DemoUserSeeder.DEMO_USERS.size() - 1)).update(anyString(), anyMap());
    }

    @Test
    @DisplayName("run leaves a demo user seeded with an old random id in place (it only logs a warning)")
    void run_existingUserWithOtherId_isNotTouched() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("driver02@ds03.test"))
                .thenReturn(Optional.of(User.builder().userId(UUID.randomUUID()).build()));
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        assertDoesNotThrow(() -> seeder.run(null));

        verify(jdbc, times(DemoUserSeeder.DEMO_USERS.size() - 1)).update(anyString(), anyMap());
    }

    @Test
    @DisplayName("run counts a row another instance inserted first (ON CONFLICT DO NOTHING returns 0) and continues")
    void run_concurrentInsert_continues() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(jdbc.update(anyString(), anyMap())).thenReturn(0, 1);

        assertDoesNotThrow(() -> seeder.run(null));

        verify(jdbc, times(DemoUserSeeder.DEMO_USERS.size())).update(anyString(), anyMap());
    }
}
