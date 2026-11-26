package com.pathpilot.user;

import com.pathpilot.common.ApiException;
import com.pathpilot.user.dto.ProfileDtos.ProfileLinksResponse;
import com.pathpilot.user.dto.ProfileDtos.UpdateProfileLinksRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Map;

@Service
public class ProfileService {

    private final UserProfileRepository userProfileRepository;

    public ProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public ProfileLinksResponse getLinks(Long userId) {
        UserProfile profile = requireProfile(userId);
        return toLinksResponse(profile);
    }

    @Transactional
    public ProfileLinksResponse updateLinks(Long userId, UpdateProfileLinksRequest request) {
        UserProfile profile = requireProfile(userId);

        if (request.githubUsername() != null) {
            String handle = trimToNull(request.githubUsername());
            profile.setGithubUsername(handle);
            profile.setGithubUrl(handle != null ? "https://github.com/" + handle : null);
        }
        if (request.leetcodeUrl() != null) {
            String url = validateOptionalHttpUrl(request.leetcodeUrl(), "leetcodeUrl");
            profile.setLeetcodeUrl(url);
            if (url != null) {
                profile.setCodingProfileUrl(url);
            }
        }
        if (request.codingbatUrl() != null) {
            profile.setCodingbatUrl(validateOptionalHttpUrl(request.codingbatUrl(), "codingbatUrl"));
        }
        if (request.portfolioUrl() != null) {
            profile.setPortfolioUrl(validateOptionalHttpUrl(request.portfolioUrl(), "portfolioUrl"));
        }
        if (request.linkedinUrl() != null) {
            profile.setLinkedinUrl(validateOptionalHttpUrl(request.linkedinUrl(), "linkedinUrl"));
        }

        UserProfile saved = userProfileRepository.save(profile);
        return toLinksResponse(saved);
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

    private String validateOptionalHttpUrl(String raw, String field) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "URL must start with http:// or https://.",
                        Map.of(field, "URL must start with http:// or https://.")
                );
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Enter a valid URL.",
                        Map.of(field, "Enter a valid URL.")
                );
            }
            return trimmed;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Enter a valid URL.",
                    Map.of(field, "Enter a valid URL.")
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
