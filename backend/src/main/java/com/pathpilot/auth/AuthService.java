package com.pathpilot.auth;

import com.pathpilot.auth.dto.LoginRequest;
import com.pathpilot.auth.dto.RegisterRequest;
import com.pathpilot.auth.dto.UserDto;
import com.pathpilot.common.ApiException;
import com.pathpilot.security.JwtService;
import com.pathpilot.user.User;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import com.pathpilot.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,100}$");

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (request.password() == null || !PASSWORD_POLICY.matcher(request.password()).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Password does not meet security requirements.",
                    Map.of("password", "Password must be at least 8 characters and include at least one letter and one number.")
            );
        }

        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Passwords do not match.",
                    Map.of("confirmPassword", "Passwords do not match.")
            );
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = request.phone().trim();
        String fullName = request.fullName().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_EXISTS",
                    "An account with this email already exists.",
                    Map.of("email", "An account with this email already exists.")
            );
        }

        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PHONE_EXISTS",
                    "An account with this phone number is already registered.",
                    Map.of("phone", "An account with this phone number is already registered.")
            );
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setActive(true);
        User savedUser = userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(savedUser.getId());
        profile.setFullName(fullName);
        profile.setTimeZone(UserProfile.DEFAULT_TIME_ZONE);
        UserProfile savedProfile = userProfileRepository.save(profile);

        return toDto(savedUser, savedProfile);
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        String identifier = request.identifier().trim();
        Optional<User> userOpt = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier.toLowerCase(Locale.ROOT))
                : userRepository.findByPhone(identifier);

        User user = userOpt
                .filter(User::isActive)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Invalid credentials"
                ));

        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "PROFILE_MISSING",
                        "User profile not found."
                ));

        String token = jwtService.generateToken(user);
        return new LoginResult(toDto(user, profile), token);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Authentication required."
                ));

        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROFILE_MISSING",
                        "User profile not found."
                ));

        return toDto(user, profile);
    }

    private UserDto toDto(User user, UserProfile profile) {
        return new UserDto(
                user.getId(),
                profile.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                profile.getTimeZone(),
                user.getCreatedAtUtc()
        );
    }

    public record LoginResult(UserDto user, String token) {
    }
}
