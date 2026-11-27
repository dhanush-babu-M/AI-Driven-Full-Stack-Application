package com.pathpilot.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record SkillDto(
            @Size(max = 80, message = "Skill name must be 80 characters or fewer.")
            String name,

            @Size(max = 30, message = "Skill level must be Beginner, Working, or Comfortable.")
            String level
    ) {
    }

    public record ProfileLinksResponse(
            String githubUsername,
            String githubUrl,
            String leetcodeUrl,
            String codingbatUrl,
            String portfolioUrl,
            String linkedinUrl
    ) {
    }

    public record UpdateProfileLinksRequest(
            @Size(max = 500, message = "GitHub username or URL must be 500 characters or fewer.")
            String githubUsername,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String githubUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String leetcodeUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String codingbatUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String portfolioUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String linkedinUrl
    ) {
    }

    public record ProfileSettingsResponse(
            String fullName,
            String email,
            String timeZone,
            List<String> targetRoles,
            List<SkillDto> skills,
            String githubUsername,
            String githubUrl,
            String leetcodeUrl,
            String codingbatUrl,
            String portfolioUrl,
            String linkedinUrl
    ) {
    }

    public record UpdateProfileSettingsRequest(
            @Size(max = 64, message = "Time zone must be 64 characters or fewer.")
            String timeZone,

            List<@Size(max = 100, message = "Each target role must be 100 characters or fewer.") String> targetRoles,

            @Valid
            List<SkillDto> skills,

            @Size(max = 500, message = "GitHub username or URL must be 500 characters or fewer.")
            String githubInput,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String leetcodeUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String codingbatUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String portfolioUrl,

            @Size(max = 500, message = "URL must be 500 characters or fewer.")
            String linkedinUrl
    ) {
    }
}
