package com.pathpilot.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface GithubEventRepository extends JpaRepository<GithubEvent, Long> {

    List<GithubEvent> findAllByUserIdAndEventDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    List<GithubEvent> findAllByUserIdAndEventDateBetweenOrderByOccurredAtUtcDescIdDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<GithubEvent> findAllByUserIdAndEventDateOrderByOccurredAtUtcDescIdDesc(Long userId, LocalDate eventDate);

    List<GithubEvent> findAllByUserIdAndExternalEventIdIn(Long userId, Collection<String> externalEventIds);

    boolean existsByUserIdAndExternalEventId(Long userId, String externalEventId);
}
