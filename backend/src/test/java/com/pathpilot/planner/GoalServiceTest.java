package com.pathpilot.planner;

import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.common.ApiException;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.dto.GoalDtos.GoalCarryForwardRequest;
import com.pathpilot.planner.dto.GoalDtos.GoalDto;
import com.pathpilot.planner.dto.GoalDtos.GoalRequest;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock private GoalRepository goalRepository;
    @Mock private WeeklyReviewRepository weeklyReviewRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private CodingActivityRepository codingActivityRepository;
    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private ProjectMilestoneRepository projectMilestoneRepository;
    @Mock private RecordHistoryRepository recordHistoryRepository;
    @Mock private UserProfileRepository userProfileRepository;

    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(
                goalRepository, weeklyReviewRepository, taskRepository,
                codingActivityRepository, jobApplicationRepository,
                projectMilestoneRepository, recordHistoryRepository, userProfileRepository
        );
    }

    @Test
    void codingProblemsGoal_calculatesDoneCountFromCodingActivitiesWhenPresent() {
        LocalDate weekStart = LocalDate.of(2026, 11, 23);
        Goal goal = new Goal();
        goal.setId(10L);
        goal.setUserId(1L);
        goal.setTitle("Solve 15 LeetCode problems");
        goal.setCategory("CODING_PROBLEMS");
        goal.setPeriodType("WEEK");
        goal.setStartDate(weekStart);
        goal.setTargetDate(weekStart.plusDays(6));
        goal.setTargetCount(15);
        goal.setDoneCount(0);

        CodingActivity a1 = new CodingActivity();
        a1.setProblemsSolved(3);
        CodingActivity a2 = new CodingActivity();
        a2.setProblemsSolved(4);

        when(goalRepository.findAllByUserIdAndPeriodTypeAndStartDateAndDeletedAtUtcIsNullOrderByIdAsc(1L, "WEEK", weekStart))
                .thenReturn(List.of(goal));
        when(taskRepository.findAllByUserIdAndGoalIdAndDeletedAtUtcIsNull(1L, 10L)).thenReturn(List.of());
        when(codingActivityRepository.findAllByUserIdAndActivityDateBetweenOrderByActivityDateDesc(1L, weekStart, weekStart.plusDays(6)))
                .thenReturn(List.of(a1, a2));

        List<GoalDto> result = goalService.listGoals(1L, "WEEK", weekStart);
        assertEquals(1, result.size());
        assertEquals(7, result.get(0).doneCount());
        assertEquals("CALCULATED", result.get(0).doneCountSource());
    }

    @Test
    void manualGoal_fallsBackToManualTagWhenNoLinkedEntriesExist() {
        LocalDate monthStart = LocalDate.of(2026, 11, 1);
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            g.setId(21L);
            return g;
        });
        when(taskRepository.findAllByUserIdAndGoalIdAndDeletedAtUtcIsNull(1L, 21L)).thenReturn(List.of());

        GoalDto created = goalService.createGoal(1L, new GoalRequest(
                "Read Spring Boot Docs",
                "TECHNOLOGIES_TO_LEARN",
                "MONTH",
                monthStart,
                10,
                3,
                null,
                "PENDING"
        ));

        assertEquals(3, created.doneCount());
        assertEquals("MANUAL", created.doneCountSource());
    }

    @Test
    void carryForwardGoals_createsCopyInNewPeriodAndMarksOriginalRescheduled() {
        LocalDate octStart = LocalDate.of(2026, 10, 1);
        LocalDate novStart = LocalDate.of(2026, 11, 1);

        Goal orig = new Goal();
        orig.setId(5L);
        orig.setUserId(1L);
        orig.setTitle("Apply to 20 companies");
        orig.setCategory("JOB_APPLICATIONS");
        orig.setPeriodType("MONTH");
        orig.setStartDate(octStart);
        orig.setTargetDate(LocalDate.of(2026, 10, 31));
        orig.setTargetCount(20);
        orig.setStatus("PENDING");

        when(goalRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(5L, 1L)).thenReturn(Optional.of(orig));
        AtomicLong seq = new AtomicLong(50);
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            if (g.getId() == null) g.setId(seq.getAndIncrement());
            return g;
        });

        List<GoalDto> carried = goalService.carryForwardGoals(1L, new GoalCarryForwardRequest(List.of(5L), novStart));
        assertEquals(1, carried.size());
        assertEquals(5L, carried.get(0).carriedFromGoalId());
        assertEquals(novStart, carried.get(0).periodStart());
        assertEquals("RESCHEDULED", orig.getStatus());
    }

    @Test
    void otherUserGoalUpdate_throwsNotFound() {
        when(goalRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(99L, 2L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> goalService.softDeleteGoal(2L, 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
