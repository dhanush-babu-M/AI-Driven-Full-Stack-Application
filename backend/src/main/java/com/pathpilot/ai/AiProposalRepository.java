package com.pathpilot.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProposalRepository extends JpaRepository<AiProposal, Long> {

    List<AiProposal> findAllByUserIdOrderByCreatedAtUtcDescIdDesc(Long userId);

    List<AiProposal> findAllByUserIdAndStatusOrderByCreatedAtUtcDescIdDesc(Long userId, String status);

    Optional<AiProposal> findByIdAndUserId(Long id, Long userId);
}
