package com.pathpilot.planner;

import com.pathpilot.planner.dto.DailyLogDtos.DailyLogRequest;
import com.pathpilot.planner.dto.DailyLogDtos.DailyLogResponse;
import com.pathpilot.planner.dto.DailyLogDtos.LearningRecordDto;
import com.pathpilot.planner.dto.DailyLogDtos.LearningRecordRequest;
import com.pathpilot.planner.dto.DailyLogDtos.RestDayRequest;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class DailyLogController {

    private final DailyLogService dailyLogService;

    public DailyLogController(DailyLogService dailyLogService) {
        this.dailyLogService = dailyLogService;
    }

    @GetMapping("/api/daily-logs/{date}")
    public ResponseEntity<DailyLogResponse> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(dailyLogService.getByDate(CurrentUser.id(), date));
    }

    @PutMapping("/api/daily-logs/{date}")
    public ResponseEntity<DailyLogResponse> saveNote(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody DailyLogRequest request
    ) {
        return ResponseEntity.ok(dailyLogService.saveNote(CurrentUser.id(), date, request));
    }

    @PostMapping("/api/daily-logs/{date}/rest-day")
    public ResponseEntity<DailyLogResponse> confirmRestDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody(required = false) RestDayRequest request
    ) {
        return ResponseEntity.ok(dailyLogService.confirmRestDay(CurrentUser.id(), date, request));
    }

    @GetMapping("/api/learning-records")
    public ResponseEntity<List<LearningRecordDto>> listLearningRecords(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(dailyLogService.listLearningRecords(CurrentUser.id(), target));
    }

    @PostMapping("/api/learning-records")
    public ResponseEntity<LearningRecordDto> createLearningRecord(@Valid @RequestBody LearningRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dailyLogService.createLearningRecord(CurrentUser.id(), request));
    }
}
