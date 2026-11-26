package com.pathpilot.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Long> {

    Optional<ConnectedAccount> findByUserIdAndProviderIgnoreCase(Long userId, String provider);

    List<ConnectedAccount> findAllByProviderIgnoreCaseAndSyncEnabledTrue(String provider);
}
