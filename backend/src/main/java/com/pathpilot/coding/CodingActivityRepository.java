package com.pathpilot.coding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CodingActivityRepository extends JpaRepository<CodingActivity, Long> {

    List<CodingActivity> findAllByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<CodingActivity> findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<CodingActivity> findAllByUserIdAndDeletedAtUtcIsNullOrderByActivityDateDescIdDesc(Long userId);

    List<CodingActivity> findAllByUserIdAndActivityDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcAscIdAsc(
            Long userId,
            LocalDate activityDate
    );

    Optional<CodingActivity> findByIdAndUserIdAndDeletedAtUtcIsNull(Long id, Long userId);
}
