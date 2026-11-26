package com.pathpilot.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
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
            @Size(max = 100, message = "GitHub username must be 100 characters or fewer.")
            @Pattern(
                    regexp = "^$|^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$",
                    message = "Enter a valid GitHub username (letters, numbers, single hyphens)."
            )
            String githubUsername,

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
