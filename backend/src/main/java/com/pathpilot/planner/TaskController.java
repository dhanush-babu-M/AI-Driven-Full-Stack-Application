package com.pathpilot.planner;

import com.pathpilot.planner.dto.CarryForwardRequest;
import com.pathpilot.planner.dto.MonthDaySummaryDto;
import com.pathpilot.planner.dto.RecordHistoryDto;
import com.pathpilot.planner.dto.TaskDto;
import com.pathpilot.planner.dto.TaskReminderRequest;
import com.pathpilot.planner.dto.TaskRequest;
import com.pathpilot.planner.dto.TaskStatusRequest;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<TaskDto>> listByDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(taskService.listByDate(CurrentUser.id(), target));
    }

    @GetMapping("/unfinished-before")
    public ResponseEntity<List<TaskDto>> listUnfinishedBefore(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(taskService.listUnfinishedBefore(CurrentUser.id(), target));
    }

    @GetMapping("/month-summary")
    public ResponseEntity<List<MonthDaySummaryDto>> monthSummary(@RequestParam String month) {
        YearMonth ym = YearMonth.parse(month);
        return ResponseEntity.ok(taskService.getMonthSummary(CurrentUser.id(), ym));
    }

    @PostMapping
    public ResponseEntity<TaskDto> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(CurrentUser.id(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDto> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(CurrentUser.id(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TaskStatusRequest request
    ) {
        return ResponseEntity.ok(taskService.updateStatus(CurrentUser.id(), id, request));
    }

    @PatchMapping("/{id}/reminder")
    public ResponseEntity<TaskDto> updateReminder(
            @PathVariable Long id,
            @RequestBody TaskReminderRequest request
    ) {
        return ResponseEntity.ok(taskService.updateReminder(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> softDelete(@PathVariable Long id) {
        taskService.softDelete(CurrentUser.id(), id);
        return ResponseEntity.ok(Map.of("message", "Task deleted."));
    }

    @PostMapping("/carry-forward")
    public ResponseEntity<List<TaskDto>> carryForward(@Valid @RequestBody CarryForwardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.carryForward(CurrentUser.id(), request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<RecordHistoryDto>> history(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskHistory(CurrentUser.id(), id));
    }
}
