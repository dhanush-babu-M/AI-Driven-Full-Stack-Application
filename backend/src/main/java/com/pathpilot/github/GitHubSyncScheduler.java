package com.pathpilot.github;

import com.pathpilot.common.ApiException;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled GitHub sync running twice daily (02:00 and 14:00) for connected accounts.
 * Uses conditional ETags and handles rate-limit/network errors per account safely.
 */
@Component
public class GitHubSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GitHubSyncScheduler.class);

    private final ConnectedAccountRepository connectedAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final GitHubService gitHubService;

    public GitHubSyncScheduler(
            ConnectedAccountRepository connectedAccountRepository,
            UserProfileRepository userProfileRepository,
            GitHubService gitHubService
    ) {
        this.connectedAccountRepository = connectedAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.gitHubService = gitHubService;
    }

    @Scheduled(cron = "0 0 2,14 * * *")
    public void syncAllConnectedAccounts() {
        List<ConnectedAccount> accounts = connectedAccountRepository
                .findAllByProviderIgnoreCaseAndSyncEnabledTrue(ConnectedAccount.PROVIDER_GITHUB);

        for (ConnectedAccount account : accounts) {
            try {
                UserProfile profile = userProfileRepository.findByUserId(account.getUserId()).orElse(null);
                if (profile == null) {
                    continue;
                }
                gitHubService.syncAccount(account, profile);
            } catch (ApiException ex) {
                log.warn("Scheduled GitHub sync failed for userId={}: {}", account.getUserId(), ex.getMessage());
                account.setLastSyncStatus("GITHUB_RATE_LIMITED".equals(ex.getCode()) ? "RATE_LIMITED" : "ERROR");
                account.setLastSyncError(ex.getMessage());
                connectedAccountRepository.save(account);
            } catch (Exception ex) {
                log.warn("Unexpected scheduled GitHub sync error for userId={}: {}", account.getUserId(), ex.getMessage());
            }
        }
    }
}
