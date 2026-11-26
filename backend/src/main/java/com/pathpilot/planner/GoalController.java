package com.pathpilot.planner;

import com.pathpilot.planner.dto.GoalDtos.GoalCarryForwardRequest;
import com.pathpilot.planner.dto.GoalDtos.GoalDto;
import com.pathpilot.planner.dto.GoalDtos.GoalRequest;
import com.pathpilot.planner.dto.GoalDtos.WeeklyReviewDto;
import com.pathpilot.planner.dto.GoalDtos.WeeklyReviewRequest;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/api/goals")
    public ResponseEntity<List<GoalDto>> listGoals(
            @RequestParam String scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start
    ) {
        return ResponseEntity.ok(goalService.listGoals(CurrentUser.id(), scope, start));
    }

    @PostMapping("/api/goals")
    public ResponseEntity<GoalDto> createGoal(@Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.createGoal(CurrentUser.id(), request));
    }

    @PutMapping("/api/goals/{id}")
    public ResponseEntity<GoalDto> updateGoal(@PathVariable Long id, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/api/goals/{id}")
    public ResponseEntity<Map<String, String>> deleteGoal(@PathVariable Long id) {
        goalService.softDeleteGoal(CurrentUser.id(), id);
        return ResponseEntity.ok(Map.of("message", "Goal deleted."));
    }

    @PostMapping("/api/goals/carry-forward")
    public ResponseEntity<List<GoalDto>> carryForwardGoals(@Valid @RequestBody GoalCarryForwardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(goalService.carryForwardGoals(CurrentUser.id(), request));
    }

    @GetMapping("/api/planner/weekly-review")
    public ResponseEntity<WeeklyReviewDto> getWeeklyReview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start
    ) {
        return ResponseEntity.ok(goalService.getWeeklyReview(CurrentUser.id(), start));
    }

    @PostMapping("/api/planner/weekly-review")
    public ResponseEntity<WeeklyReviewDto> saveWeeklyReview(@Valid @RequestBody WeeklyReviewRequest request) {
        return ResponseEntity.ok(goalService.saveWeeklyReview(CurrentUser.id(), request));
    }
}
