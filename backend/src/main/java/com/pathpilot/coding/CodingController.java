package com.pathpilot.coding;

import com.pathpilot.coding.dto.CodingDtos.CodingActivityDto;
import com.pathpilot.coding.dto.CodingDtos.CodingActivityRequest;
import com.pathpilot.coding.dto.CodingDtos.CodingPageResponse;
import com.pathpilot.coding.dto.CodingDtos.CodingStreakSummaryResponse;
import com.pathpilot.coding.dto.CodingDtos.DayActivityDetailResponse;
import com.pathpilot.coding.dto.CodingDtos.HeatmapResponse;
import com.pathpilot.planner.dto.RecordHistoryDto;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/coding")
public class CodingController {

    private final CodingService codingService;

    public CodingController(CodingService codingService) {
        this.codingService = codingService;
    }

    @GetMapping
    public CodingPageResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return codingService.listActivities(CurrentUser.id(), from, to, platform, topic, result, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CodingActivityDto create(@Valid @RequestBody CodingActivityRequest request) {
        return codingService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public CodingActivityDto update(@PathVariable Long id, @Valid @RequestBody CodingActivityRequest request) {
        return codingService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        codingService.delete(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/history")
    public List<RecordHistoryDto> history(@PathVariable Long id) {
        return codingService.getHistory(CurrentUser.id(), id);
    }

    @GetMapping("/heatmap")
    public HeatmapResponse heatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "combined") String source
    ) {
        return codingService.getHeatmap(CurrentUser.id(), from, to, source);
    }

    @GetMapping("/day/{date}")
    public DayActivityDetailResponse dayDetails(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return codingService.getDayDetails(CurrentUser.id(), date);
    }

    @GetMapping("/streak")
    public CodingStreakSummaryResponse streak(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return codingService.getStreakAndSummary(CurrentUser.id(), from, to);
    }
}
