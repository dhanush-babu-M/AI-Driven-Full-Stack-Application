package com.pathpilot.github;

import com.pathpilot.common.ApiException;
import com.pathpilot.github.GitHubClient.GitHubEventsFetchResult;
import com.pathpilot.github.GitHubClient.GitHubUserInfo;
import com.pathpilot.github.GitHubClient.ParsedGitHubEvent;
import com.pathpilot.github.dto.GitHubDtos.GitHubConnectRequest;
import com.pathpilot.github.dto.GitHubDtos.GitHubEventDto;
import com.pathpilot.github.dto.GitHubDtos.GitHubRepoDto;
import com.pathpilot.github.dto.GitHubDtos.GitHubStatusResponse;
import com.pathpilot.github.dto.GitHubDtos.GitHubSyncResponse;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.security.TokenEncryptionService;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GitHubService {

    private final UserProfileRepository userProfileRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final GithubEventRepository githubEventRepository;
    private final GitHubClient gitHubClient;
    private final TokenEncryptionService tokenEncryptionService;

    public GitHubService(
            UserProfileRepository userProfileRepository,
            ConnectedAccountRepository connectedAccountRepository,
            GithubEventRepository githubEventRepository,
            GitHubClient gitHubClient,
            TokenEncryptionService tokenEncryptionService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.connectedAccountRepository = connectedAccountRepository;
        this.githubEventRepository = githubEventRepository;
        this.gitHubClient = gitHubClient;
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Transactional
    public GitHubStatusResponse connect(Long userId, GitHubConnectRequest request) {
        UserProfile profile = requireProfile(userId);
        String requestedUsername = request.username().trim();
        String newToken = (request.personalAccessToken() != null && !request.personalAccessToken().isBlank())
                ? request.personalAccessToken().trim()
                : null;

        ConnectedAccount account = connectedAccountRepository
                .findByUserIdAndProviderIgnoreCase(userId, ConnectedAccount.PROVIDER_GITHUB)
                .orElseGet(() -> {
                    ConnectedAccount created = new ConnectedAccount();
                    created.setUserId(userId);
                    created.setProvider(ConnectedAccount.PROVIDER_GITHUB);
                    return created;
                });

        String tokenToUse = newToken;
        if (tokenToUse == null && !Boolean.TRUE.equals(request.clearToken()) && account.getEncryptedToken() != null) {
            tokenToUse = tokenEncryptionService.decrypt(account.getEncryptedToken());
        }

        GitHubUserInfo userInfo = gitHubClient.verifyUser(requestedUsername, tokenToUse);

        boolean usernameChanged = account.getUsernameOrHandle() == null
                || !account.getUsernameOrHandle().equalsIgnoreCase(userInfo.login());

        profile.setGithubUsername(userInfo.login());
        profile.setGithubUrl(userInfo.htmlUrl());
        userProfileRepository.save(profile);

        account.setUsernameOrHandle(userInfo.login());
        account.setProfileUrl(userInfo.htmlUrl());
        account.setSyncEnabled(true);
        if (Boolean.TRUE.equals(request.clearToken())) {
            account.setEncryptedToken(null);
            account.setEtag(null);
        } else if (newToken != null) {
            account.setEncryptedToken(tokenEncryptionService.encrypt(newToken));
            account.setEtag(null);
        } else if (usernameChanged) {
            account.setEtag(null);
        }
        account = connectedAccountRepository.save(account);

        try {
            syncAccount(account, profile);
        } catch (ApiException ex) {
            account.setLastSyncStatus("RATE_LIMITED".equals(ex.getCode()) ? "RATE_LIMITED" : "ERROR");
            account.setLastSyncError(ex.getMessage());
            connectedAccountRepository.save(account);
        }

        return getStatus(userId);
    }

    @Transactional(readOnly = true)
    public GitHubStatusResponse getStatus(Long userId) {
        UserProfile profile = requireProfile(userId);
        ConnectedAccount account = connectedAccountRepository
                .findByUserIdAndProviderIgnoreCase(userId, ConnectedAccount.PROVIDER_GITHUB)
                .orElse(null);

        String username = account != null ? account.getUsernameOrHandle() : profile.getGithubUsername();
        String profileUrl = account != null ? account.getProfileUrl() : profile.getGithubUrl();
        boolean connected = username != null && !username.isBlank();
        long storedEvents = githubEventRepository
                .findAllByUserIdAndEventDateBetween(userId, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31))
                .size();

        return new GitHubStatusResponse(
                connected,
                username,
                profileUrl,
                account != null && account.getEncryptedToken() != null && !account.getEncryptedToken().isBlank(),
                account != null ? account.getLastSyncedAtUtc() : null,
                account != null ? account.getLastSyncStatus() : null,
                account != null ? account.getLastSyncError() : null,
                storedEvents
        );
    }

    @Transactional
    public GitHubSyncResponse syncForUser(Long userId) {
        UserProfile profile = requireProfile(userId);
        ConnectedAccount account = connectedAccountRepository
                .findByUserIdAndProviderIgnoreCase(userId, ConnectedAccount.PROVIDER_GITHUB)
                .orElseGet(() -> bootstrapFromProfile(profile));

        try {
            return syncAccount(account, profile);
        } catch (ApiException ex) {
            account.setLastSyncStatus("GITHUB_RATE_LIMITED".equals(ex.getCode()) ? "RATE_LIMITED" : "ERROR");
            account.setLastSyncError(ex.getMessage());
            connectedAccountRepository.save(account);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<GitHubRepoDto> getRepos(Long userId) {
        UserProfile profile = requireProfile(userId);
        ConnectedAccount account = connectedAccountRepository
                .findByUserIdAndProviderIgnoreCase(userId, ConnectedAccount.PROVIDER_GITHUB)
                .orElse(null);

        String username = account != null ? account.getUsernameOrHandle() : profile.getGithubUsername();
        if (username == null || username.isBlank()) {
            return List.of();
        }
        String token = (account != null && account.getEncryptedToken() != null)
                ? tokenEncryptionService.decrypt(account.getEncryptedToken())
                : null;
        return gitHubClient.fetchUserRepos(username, token);
    }

    @Transactional(readOnly = true)
    public List<GitHubEventDto> listEvents(Long userId, LocalDate from, LocalDate to) {
        UserProfile profile = requireProfile(userId);
        String tz = resolveTimeZone(profile);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "End date cannot be before start date.");
        }

        return githubEventRepository
                .findAllByUserIdAndEventDateBetweenOrderByOccurredAtUtcDescIdDesc(userId, start, end)
                .stream()
                .map(e -> new GitHubEventDto(
                        e.getId(),
                        e.getExternalEventId(),
                        e.getEventType(),
                        e.getRepoName(),
                        e.getEventDate(),
                        e.getOccurredAtUtc(),
                        e.getCommitCount(),
                        e.getPayloadSummary(),
                        e.getHtmlUrl(),
                        e.getDataSource()
                ))
                .toList();
    }

    /**
     * Core sync & de-duplication logic. Unique constraint (user_id, external_event_id)
     * plus pre-insert ID filtering guarantee re-syncing never double-counts events.
     */
    @Transactional
    public GitHubSyncResponse syncAccount(ConnectedAccount account, UserProfile profile) {
        String token = account.getEncryptedToken() != null
                ? tokenEncryptionService.decrypt(account.getEncryptedToken())
                : null;

        GitHubEventsFetchResult result = gitHubClient.fetchUserEvents(
                account.getUsernameOrHandle(),
                token,
                account.getEtag()
        );

        Instant now = Instant.now();
        if (result.notModified()) {
            account.setLastSyncedAtUtc(now);
            account.setLastSyncStatus("SUCCESS");
            account.setLastSyncError(null);
            connectedAccountRepository.save(account);
            return new GitHubSyncResponse(
                    account.getUsernameOrHandle(),
                    0,
                    0,
                    0,
                    true,
                    now,
                    "SUCCESS",
                    "No new public GitHub events since the last sync."
            );
        }

        List<ParsedGitHubEvent> fetched = result.events();
        Set<String> fetchedIds = fetched.stream()
                .map(ParsedGitHubEvent::externalEventId)
                .collect(Collectors.toSet());

        Set<String> existingIds = fetchedIds.isEmpty()
                ? Set.of()
                : githubEventRepository.findAllByUserIdAndExternalEventIdIn(account.getUserId(), fetchedIds)
                        .stream()
                        .map(GithubEvent::getExternalEventId)
                        .collect(Collectors.toSet());

        ZoneId userZone = safeZoneId(resolveTimeZone(profile));
        Set<String> seenInBatch = new HashSet<>(existingIds);
        List<GithubEvent> toInsert = new ArrayList<>();
        int skipped = 0;

        for (ParsedGitHubEvent item : fetched) {
            if (!seenInBatch.add(item.externalEventId())) {
                skipped++;
                continue;
            }
            GithubEvent entity = new GithubEvent();
            entity.setUserId(account.getUserId());
            entity.setConnectedAccountId(account.getId());
            entity.setExternalEventId(item.externalEventId());
            entity.setEventType(item.eventType());
            entity.setRepoName(item.repoName());
            entity.setOccurredAtUtc(item.occurredAtUtc());
            entity.setEventDate(item.occurredAtUtc().atZone(userZone).toLocalDate());
            entity.setCommitCount(item.commitCount());
            entity.setPayloadSummary(item.payloadSummary());
            entity.setHtmlUrl(item.htmlUrl());
            entity.setDataSource("IMPORTED");
            toInsert.add(entity);
        }

        if (!toInsert.isEmpty()) {
            githubEventRepository.saveAll(toInsert);
        }

        account.setEtag(result.etag());
        account.setLastSyncedAtUtc(now);
        account.setLastSyncStatus("SUCCESS");
        account.setLastSyncError(null);
        connectedAccountRepository.save(account);

        return new GitHubSyncResponse(
                account.getUsernameOrHandle(),
                fetched.size(),
                toInsert.size(),
                skipped,
                false,
                now,
                "SUCCESS",
                toInsert.isEmpty()
                        ? "Synced with GitHub. All " + skipped + " fetched event(s) were already imported."
                        : "Imported " + toInsert.size() + " new GitHub event(s) (" + skipped + " duplicate(s) skipped)."
        );
    }

    private ConnectedAccount bootstrapFromProfile(UserProfile profile) {
        if (profile.getGithubUsername() == null || profile.getGithubUsername().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GITHUB_NOT_CONNECTED",
                    "Connect your GitHub username before syncing."
            );
        }
        ConnectedAccount account = new ConnectedAccount();
        account.setUserId(profile.getUserId());
        account.setProvider(ConnectedAccount.PROVIDER_GITHUB);
        account.setUsernameOrHandle(profile.getGithubUsername().trim());
        account.setProfileUrl(
                profile.getGithubUrl() != null && !profile.getGithubUrl().isBlank()
                        ? profile.getGithubUrl()
                        : "https://github.com/" + profile.getGithubUsername().trim()
        );
        account.setSyncEnabled(true);
        return connectedAccountRepository.save(account);
    }

    private UserProfile requireProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_MISSING", "User profile not found."));
    }

    private String resolveTimeZone(UserProfile profile) {
        String tz = profile.getTimeZone();
        return (tz == null || tz.isBlank()) ? UserProfile.DEFAULT_TIME_ZONE : tz;
    }

    private ZoneId safeZoneId(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            return ZoneId.of(UserProfile.DEFAULT_TIME_ZONE);
        }
    }
}
