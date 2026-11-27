package com.pathpilot.user;

import com.pathpilot.common.ApiException;
import com.pathpilot.user.dto.ProfileDtos.ProfileSettingsResponse;
import com.pathpilot.user.dto.ProfileDtos.SkillDto;
import com.pathpilot.user.dto.ProfileDtos.UpdateProfileSettingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private UserProfileRepository userProfileRepository;
    private UserRepository userRepository;
    private ProfileService profileService;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        userProfileRepository = mock(UserProfileRepository.class);
        userRepository = mock(UserRepository.class);
        profileService = new ProfileService(userProfileRepository, userRepository, new ObjectMapper());

        profile = new UserProfile();
        profile.setId(1L);
        profile.setUserId(1L);
        profile.setFullName("Alex Johnson");
        profile.setTimeZone("Asia/Kolkata");

        User user = new User();
        user.setId(1L);
        user.setEmail("alex@example.com");

        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void profileUrls_requireHttpsScheme() {
        ApiException ex = assertThrows(ApiException.class, () -> profileService.updateProfileSettings(
                1L,
                new UpdateProfileSettingsRequest(
                        "Asia/Kolkata",
                        List.of(),
                        List.of(),
                        "octocat",
                        "http://leetcode.com/u/octocat",
                        null,
                        null,
                        null
                )
        ));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("URL must start with https://.", ex.getFieldErrors().get("leetcodeUrl"));
    }

    @Test
    void profileSettings_acceptsGithubRepoUrlSkillsAndTargetRoles() {
        ProfileSettingsResponse updated = profileService.updateProfileSettings(
                1L,
                new UpdateProfileSettingsRequest(
                        "America/New_York",
                        List.of("Java Full Stack Developer", "Backend Engineer"),
                        List.of(
                                new SkillDto("Java", "Comfortable"),
                                new SkillDto("Spring Boot", "Working"),
                                new SkillDto("Docker", "Beginner")
                        ),
                        "https://github.com/octocat/hello-world",
                        "https://leetcode.com/u/octocat",
                        "https://codingbat.com/java",
                        "https://alex.dev",
                        "https://linkedin.com/in/octocat"
                )
        );

        assertEquals("America/New_York", updated.timeZone());
        assertEquals(2, updated.targetRoles().size());
        assertEquals("Java Full Stack Developer", profile.getTargetRole());
        assertEquals(3, updated.skills().size());
        assertEquals("octocat", updated.githubUsername());
        assertEquals("https://github.com/octocat/hello-world", updated.githubUrl());
        assertEquals("https://alex.dev", updated.portfolioUrl());
    }
}
