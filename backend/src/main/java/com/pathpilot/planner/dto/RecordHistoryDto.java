package com.pathpilot.planner.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RecordHistoryDto(
        Long id,
        String entityType,
        Long entityId,
        String actionType,
        String actorSource,
        LocalDate recordDate,
        String changeSummary,
        Instant createdAtUtc
) {
}
