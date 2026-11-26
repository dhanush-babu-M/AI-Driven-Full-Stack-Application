package com.pathpilot.planner;

import com.pathpilot.common.ApiException;
import com.pathpilot.planner.dto.CarryForwardRequest;
import com.pathpilot.planner.dto.TaskDto;
import com.pathpilot.planner.dto.TaskRequest;
import com.pathpilot.planner.dto.TaskStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private DailyLogRepository dailyLogRepository;

    @Mock
    private RecordHistoryRepository recordHistoryRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, dailyLogRepository, recordHistoryRepository);
    }

    @Test
    void create_rejectsEndTimeNotAfterStartTime() {
        TaskRequest badRequest = new TaskRequest(
                "Eat breakfast - puri",
                null,
                "MEALS_PERSONAL",
                LocalDate.of(2026, 11, 26),
                LocalTime.of(9, 0),
                LocalTime.of(8, 30),
                "MEDIUM",
                "PLANNED",
                null
        );

        ApiException ex = assertThrows(ApiException.class, () -> taskService.create(1L, badRequest));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getFieldErrors().containsKey("endTime"));
    }

    @Test
    void carryForward_copiesUnfinishedTasksAndMarksOriginalsRescheduled() {
        LocalDate yesterday = LocalDate.of(2026, 11, 25);
        LocalDate today = LocalDate.of(2026, 11, 26);

        Task orig1 = buildTask(10L, 1L, "Revise Joins", yesterday, "PENDING", "SQL_MYSQL");
        Task orig2 = buildTask(11L, 1L, "Build Navbar", yesterday, "PLANNED", "REACT_FRONTEND");

        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(10L, 1L)).thenReturn(Optional.of(orig1));
        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(11L, 1L)).thenReturn(Optional.of(orig2));
        when(dailyLogRepository.findByUserIdAndLogDate(1L, today)).thenReturn(Optional.empty());

        AtomicLong idSeq = new AtomicLong(100);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(idSeq.getAndIncrement());
            return t;
        });

        List<TaskDto> carried = taskService.carryForward(1L, new CarryForwardRequest(List.of(10L, 11L), today));

        assertEquals(2, carried.size());
        assertEquals(10L, carried.get(0).carriedFromTaskId());
        assertEquals("PLANNED", carried.get(0).status());
        assertEquals(today, carried.get(0).taskDate());

        assertEquals("RESCHEDULED", orig1.getStatus());
        assertEquals(today, orig1.getRescheduledToDate());
        assertNotNull(orig1.getRescheduledToTaskId());

        assertEquals("RESCHEDULED", orig2.getStatus());
        assertEquals(today, orig2.getRescheduledToDate());
    }

    @Test
    void carryForward_rejectsCompletedTask() {
        LocalDate yesterday = LocalDate.of(2026, 11, 25);
        LocalDate today = LocalDate.of(2026, 11, 26);
        Task completed = buildTask(15L, 1L, "Already done", yesterday, "COMPLETED", "JAVA_LEARNING");

        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(15L, 1L)).thenReturn(Optional.of(completed));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> taskService.carryForward(1L, new CarryForwardRequest(List.of(15L), today))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_CARRY_FORWARD", ex.getCode());
    }

    @Test
    void updatePastTask_recordsOldValuesInRecordHistory() {
        LocalDate pastDate = LocalDate.of(2026, 11, 20);
        Task existing = buildTask(20L, 1L, "Old Title", pastDate, "PENDING", "JAVA_LEARNING");

        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(20L, 1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskRequest edit = new TaskRequest(
                "Updated Title",
                "Updated description",
                "SPRING_BOOT",
                pastDate,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "HIGH",
                "COMPLETED",
                "Done"
        );

        taskService.update(1L, 20L, edit);

        ArgumentCaptor<RecordHistory> historyCaptor = ArgumentCaptor.forClass(RecordHistory.class);
        verify(recordHistoryRepository, atLeastOnce()).save(historyCaptor.capture());
        RecordHistory recorded = historyCaptor.getValue();
        assertEquals("UPDATED", recorded.getActionType());
        assertTrue(recorded.getChangeSummary().contains("Old Title"));
        assertTrue(recorded.getChangeSummary().contains("Updated Title"));
    }

    @Test
    void rescheduleStatus_createsLinkedNewTaskAndPreservesOld() {
        LocalDate day1 = LocalDate.of(2026, 11, 25);
        LocalDate day2 = LocalDate.of(2026, 11, 27);
        Task existing = buildTask(30L, 1L, "Practice Arrays", day1, "PLANNED", "CODING_PRACTICE");

        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(30L, 1L)).thenReturn(Optional.of(existing));
        AtomicLong seq = new AtomicLong(300);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(seq.getAndIncrement());
            return t;
        });

        TaskDto updated = taskService.updateStatus(1L, 30L, new TaskStatusRequest("RESCHEDULED", day2));
        assertEquals("RESCHEDULED", updated.status());
        assertEquals(day2, updated.rescheduledToDate());
        assertEquals(300L, updated.rescheduledToTaskId());
    }

    @Test
    void anotherUserCannotAccessTask() {
        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(99L, 2L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> taskService.getTaskHistory(2L, 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    private Task buildTask(Long id, Long userId, String title, LocalDate date, String status, String category) {
        Task task = new Task();
        task.setId(id);
        task.setUserId(userId);
        task.setTitle(title);
        task.setTaskDate(date);
        task.setStatus(status);
        task.setCategory(category);
        task.setPriority(2);
        task.setDataSource("MANUAL");
        return task;
    }
}
