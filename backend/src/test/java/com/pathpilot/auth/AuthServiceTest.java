package com.pathpilot.auth;

import com.pathpilot.auth.dto.RegisterRequest;
import com.pathpilot.auth.dto.UserDto;
import com.pathpilot.common.ApiException;
import com.pathpilot.config.AppProperties;
import com.pathpilot.security.JwtService;
import com.pathpilot.user.User;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import com.pathpilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        AppProperties props = new AppProperties(
                "PathPilot",
                "Track . Learn . Grow",
                new AppProperties.CorsProperties(List.of("http://localhost:5173")),
                new AppProperties.JwtProperties(
                        "unit-test-secret-key-at-least-32-bytes-long-1234",
                        30,
                        "pathpilot_token",
                        false,
                        "Lax"
                ),
                new AppProperties.RateLimitProperties(5, 60),
                null
        );
        JwtService jwtService = new JwtService(props, JsonMapper.builder().build());
        authService = new AuthService(userRepository, userProfileRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("alex@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "Alex Johnson",
                "9876543210",
                "Alex@Example.com",
                "Password123",
                "Password123"
        );

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("EMAIL_EXISTS", ex.getCode());
        verify(userRepository, never()).save(any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void register_hashesPasswordWithBcryptAndCreatesProfileInAsiaKolkata() {
        when(userRepository.existsByEmailIgnoreCase("alex@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(42L);
            u.setCreatedAtUtc(Instant.now());
            return u;
        });
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile p = invocation.getArgument(0);
            p.setId(99L);
            return p;
        });

        RegisterRequest request = new RegisterRequest(
                "Alex Johnson",
                "9876543210",
                "alex@example.com",
                "Password123",
                "Password123"
        );

        UserDto result = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertNotEquals("Password123", savedUser.getPasswordHash());
        assertTrue(savedUser.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("Password123", savedUser.getPasswordHash()));

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertEquals("Asia/Kolkata", profileCaptor.getValue().getTimeZone());

        assertEquals(42L, result.id());
        assertEquals("Alex Johnson", result.fullName());
        assertEquals("Asia/Kolkata", result.timeZone());
    }

    @Test
    void register_rejectsWeakPassword() {
        RegisterRequest weakRequest = new RegisterRequest(
                "Alex Johnson",
                "9876543210",
                "alex@example.com",
                "onlyletters",
                "onlyletters"
        );

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(weakRequest));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).save(any());
    }
}
