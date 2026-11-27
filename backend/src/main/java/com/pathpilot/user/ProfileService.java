package com.pathpilot.user;

import com.pathpilot.common.ApiException;
import com.pathpilot.common.UrlValidationUtil;
import com.pathpilot.user.dto.ProfileDtos.ProfileLinksResponse;
import com.pathpilot.user.dto.ProfileDtos.ProfileSettingsResponse;
import com.pathpilot.user.dto.ProfileDtos.SkillDto;
import com.pathpilot.user.dto.ProfileDtos.UpdateProfileLinksRequest;
import com.pathpilot.user.dto.ProfileDtos.UpdateProfileSettingsRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProfileService {

    private static final Pattern GITHUB_HANDLE_PATTERN =
            Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$");
    private static final Set<String> ALLOWED_SKILL_LEVELS = Set.of("Beginner", "Working", "Comfortable");

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ProfileService(
            UserProfileRepository userProfileRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProfileLinksResponse getLinks(Long userId) {
        UserProfile profile = requireProfile(userId);
        return toLinksResponse(profile);
    }

    @Transactional
    public ProfileLinksResponse updateLinks(Long userId, UpdateProfileLinksRequest request) {
        UserProfile profile = requireProfile(userId);

        String githubRaw = request.githubUrl() != null ? request.githubUrl() : request.githubUsername();
        if (githubRaw != null) {
            applyGithubInput(profile, githubRaw, "githubUsername");
        }
        if (request.leetcodeUrl() != null) {
            String url = UrlValidationUtil.validateOptionalHttpsUrl(request.leetcodeUrl(), "leetcodeUrl");
            profile.setLeetcodeUrl(url);
            profile.setCodingProfileUrl(url);
        }
        if (request.codingbatUrl() != null) {
            profile.setCodingbatUrl(UrlValidationUtil.validateOptionalHttpsUrl(request.codingbatUrl(), "codingbatUrl"));
        }
        if (request.portfolioUrl() != null) {
            profile.setPortfolioUrl(UrlValidationUtil.validateOptionalHttpsUrl(request.portfolioUrl(), "portfolioUrl"));
        }
        if (request.linkedinUrl() != null) {
            profile.setLinkedinUrl(UrlValidationUtil.validateOptionalHttpsUrl(request.linkedinUrl(), "linkedinUrl"));
        }

        UserProfile saved = userProfileRepository.save(profile);
        return toLinksResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProfileSettingsResponse getProfileSettings(Long userId) {
        UserProfile profile = requireProfile(userId);
        String email = userRepository.findById(userId).map(User::getEmail).orElse("");
        return toSettingsResponse(profile, email);
    }

    @Transactional
    public ProfileSettingsResponse updateProfileSettings(Long userId, UpdateProfileSettingsRequest request) {
        UserProfile profile = requireProfile(userId);

        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            String tz = request.timeZone().trim();
            try {
                ZoneId.of(tz);
            } catch (Exception ex) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Invalid time zone.",
                        Map.of("timeZone", "Select a valid IANA time zone.")
                );
            }
            profile.setTimeZone(tz);
        }

        if (request.targetRoles() != null) {
            List<String> roles = normalizeRoles(request.targetRoles());
            profile.setTargetRolesJson(writeJson(roles));
            profile.setTargetRole(roles.isEmpty() ? null : roles.get(0));
        }

        if (request.skills() != null) {
            List<SkillDto> skills = normalizeSkills(request.skills());
            profile.setSkillsJson(writeJson(skills));
        }

        if (request.githubInput() != null) {
            applyGithubInput(profile, request.githubInput(), "githubInput");
        }
        if (request.leetcodeUrl() != null) {
            String url = UrlValidationUtil.validateOptionalHttpsUrl(request.leetcodeUrl(), "leetcodeUrl");
            profile.setLeetcodeUrl(url);
            profile.setCodingProfileUrl(url);
        }
        if (request.codingbatUrl() != null) {
            profile.setCodingbatUrl(UrlValidationUtil.validateOptionalHttpsUrl(request.codingbatUrl(), "codingbatUrl"));
        }
        if (request.portfolioUrl() != null) {
            profile.setPortfolioUrl(UrlValidationUtil.validateOptionalHttpsUrl(request.portfolioUrl(), "portfolioUrl"));
        }
        if (request.linkedinUrl() != null) {
            profile.setLinkedinUrl(UrlValidationUtil.validateOptionalHttpsUrl(request.linkedinUrl(), "linkedinUrl"));
        }

        UserProfile saved = userProfileRepository.save(profile);
        String email = userRepository.findById(userId).map(User::getEmail).orElse("");
        return toSettingsResponse(saved, email);
    }

    private void applyGithubInput(UserProfile profile, String rawInput, String field) {
        String trimmed = UrlValidationUtil.trimToNull(rawInput);
        if (trimmed == null) {
            profile.setGithubUsername(null);
            profile.setGithubUrl(null);
            return;
        }

        if (trimmed.contains("://") || trimmed.toLowerCase(Locale.ROOT).startsWith("http")) {
            String validUrl = UrlValidationUtil.validateOptionalHttpsUrl(trimmed, field);
            URI uri = URI.create(validUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"github.com".equals(host) && !"www.github.com".equals(host)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "GitHub URL must point to https://github.com/...",
                        Map.of(field, "GitHub URL must point to https://github.com/...")
                );
            }
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
            if (path.isEmpty()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Enter a GitHub profile or repository URL.",
                        Map.of(field, "Include a username or repository path on github.com.")
                );
            }
            String firstSegment = path.split("/")[0];
            if (!GITHUB_HANDLE_PATTERN.matcher(firstSegment).matches()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Invalid GitHub username in URL.",
                        Map.of(field, "Enter a valid GitHub username or repository URL.")
                );
            }
            profile.setGithubUsername(firstSegment);
            profile.setGithubUrl(validUrl);
            return;
        }

        String cleanedHandle = trimmed.startsWith("@") ? trimmed.substring(1).trim() : trimmed;
        if (!GITHUB_HANDLE_PATTERN.matcher(cleanedHandle).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Enter a valid GitHub username or https://github.com/... URL.",
                    Map.of(field, "Enter a valid GitHub username (letters, numbers, single hyphens) or https://github.com/... URL.")
            );
        }
        profile.setGithubUsername(cleanedHandle);
        profile.setGithubUrl("https://github.com/" + cleanedHandle);
    }

    private List<String> normalizeRoles(List<String> rawRoles) {
        List<String> result = new ArrayList<>();
        for (String role : rawRoles) {
            if (role == null) continue;
            String trimmed = role.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<SkillDto> normalizeSkills(List<SkillDto> rawSkills) {
        Map<String, SkillDto> dedup = new LinkedHashMap<>();
        for (SkillDto item : rawSkills) {
            if (item == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            String name = item.name().trim();
            String level = normalizeSkillLevel(item.level());
            dedup.put(name.toLowerCase(Locale.ROOT), new SkillDto(name, level));
        }
        return new ArrayList<>(dedup.values());
    }

    private String normalizeSkillLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Working";
        }
        for (String allowed : ALLOWED_SKILL_LEVELS) {
            if (allowed.equalsIgnoreCase(raw.trim())) {
                return allowed;
            }
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid skill level.",
                Map.of("skills", "Skill level must be Beginner, Working, or Comfortable.")
        );
    }

    private UserProfile requireProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_MISSING", "User profile not found."));
    }

    private ProfileLinksResponse toLinksResponse(UserProfile profile) {
        String leetcode = profile.getLeetcodeUrl();
        if (leetcode == null || leetcode.isBlank()) {
            leetcode = profile.getCodingProfileUrl();
        }
        return new ProfileLinksResponse(
                profile.getGithubUsername(),
                profile.getGithubUrl(),
                leetcode,
                profile.getCodingbatUrl(),
                profile.getPortfolioUrl(),
                profile.getLinkedinUrl()
        );
    }

    private ProfileSettingsResponse toSettingsResponse(UserProfile profile, String email) {
        ProfileLinksResponse links = toLinksResponse(profile);
        List<String> roles = readTargetRoles(profile);
        List<SkillDto> skills = readSkills(profile);
        return new ProfileSettingsResponse(
                profile.getFullName(),
                email,
                profile.getTimeZone() != null ? profile.getTimeZone() : UserProfile.DEFAULT_TIME_ZONE,
                roles,
                skills,
                links.githubUsername(),
                links.githubUrl(),
                links.leetcodeUrl(),
                links.codingbatUrl(),
                links.portfolioUrl(),
                links.linkedinUrl()
        );
    }

    private List<String> readTargetRoles(UserProfile profile) {
        if (profile.getTargetRolesJson() != null && !profile.getTargetRolesJson().isBlank()) {
            try {
                return objectMapper.readValue(profile.getTargetRolesJson(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        if (profile.getTargetRole() != null && !profile.getTargetRole().isBlank()) {
            return List.of(profile.getTargetRole().trim());
        }
        return List.of();
    }

    private List<SkillDto> readSkills(UserProfile profile) {
        if (profile.getSkillsJson() != null && !profile.getSkillsJson().isBlank()) {
            try {
                return objectMapper.readValue(profile.getSkillsJson(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR", "Unable to save profile data.");
        }
    }
}
