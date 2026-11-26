package com.pathpilot.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class GitHubDtos {

    private GitHubDtos() {
    }

    public record GitHubConnectRequest(
            @NotBlank(message = "GitHub username is required.")
            @Size(max = 100, message = "GitHub username must be 100 characters or fewer.")
            @Pattern(
                    regexp = "^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$",
                    message = "Enter a valid GitHub username (letters, numbers, single hyphens)."
            )
            String username,

            @Size(max = 500, message = "Token must be 500 characters or fewer.")
            String personalAccessToken,

            Boolean clearToken
    ) {
    }

    public record GitHubStatusResponse(
            boolean connected,
            String username,
            String profileUrl,
            boolean hasToken,
            Instant lastSyncedAtUtc,
            String lastSyncStatus,
            String lastSyncError,
            long storedEventsCount
    ) {
    }

    public record GitHubSyncResponse(
            String username,
            int fetchedEvents,
            int newlyImportedEvents,
            int skippedDuplicates,
            boolean notModified,
            Instant syncedAtUtc,
            String status,
            String message
    ) {
    }

    public record GitHubRepoDto(
            String name,
            String fullName,
            String language,
            Instant pushedAtUtc,
            String htmlUrl,
            String description,
            int stars,
            boolean isFork,
            boolean isPrivate
    ) {
    }

    public record GitHubEventDto(
            Long id,
            String externalEventId,
            String eventType,
            String repoName,
            LocalDate eventDate,
            Instant occurredAtUtc,
            int commitCount,
            String payloadSummary,
            String htmlUrl,
            String dataSource
    ) {
    }
}
