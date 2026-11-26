package com.pathpilot.coding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CodingActivityRepository extends JpaRepository<CodingActivity, Long> {

    List<CodingActivity> findAllByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
