package com.pathpilot.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GithubEventRepository extends JpaRepository<GithubEvent, Long> {

    List<GithubEvent> findAllByUserIdAndEventDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
