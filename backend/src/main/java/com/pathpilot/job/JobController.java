package com.pathpilot.job;

import com.pathpilot.job.dto.JobDtos.FollowupRescheduleRequest;
import com.pathpilot.job.dto.JobDtos.JobApplicationDetailDto;
import com.pathpilot.job.dto.JobDtos.JobApplicationRequest;
import com.pathpilot.job.dto.JobDtos.JobChecklistRequest;
import com.pathpilot.job.dto.JobDtos.JobFollowupDto;
import com.pathpilot.job.dto.JobDtos.JobPageResponse;
import com.pathpilot.job.dto.JobDtos.JobStatsDto;
import com.pathpilot.job.dto.JobDtos.JobStatusChangeRequest;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public JobPageResponse listJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return jobService.listJobs(CurrentUser.id(), search, status, from, to, page, size);
    }

    @GetMapping("/stats")
    public JobStatsDto stats() {
        return jobService.getStats(CurrentUser.id());
    }

    @GetMapping("/followups/upcoming")
    public List<JobFollowupDto> upcomingFollowups() {
        return jobService.getUpcomingFollowups(CurrentUser.id());
    }

    @PatchMapping("/followups/{followupId}/done")
    public JobFollowupDto markFollowupDone(@PathVariable Long followupId) {
        return jobService.markFollowupDone(CurrentUser.id(), followupId);
    }

    @PatchMapping("/followups/{followupId}/reschedule")
    public JobFollowupDto rescheduleFollowup(
            @PathVariable Long followupId,
            @Valid @RequestBody FollowupRescheduleRequest request
    ) {
        return jobService.rescheduleFollowup(CurrentUser.id(), followupId, request);
    }

    @GetMapping("/{id}")
    public JobApplicationDetailDto getJob(@PathVariable Long id) {
        return jobService.getJob(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplicationDetailDto createJob(@Valid @RequestBody JobApplicationRequest request) {
        return jobService.createJob(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public JobApplicationDetailDto updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationRequest request
    ) {
        return jobService.updateJob(CurrentUser.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public JobApplicationDetailDto updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody JobStatusChangeRequest request
    ) {
        return jobService.updateStatus(CurrentUser.id(), id, request);
    }

    @PutMapping("/{id}/checklist")
    public JobApplicationDetailDto updateChecklist(
            @PathVariable Long id,
            @Valid @RequestBody JobChecklistRequest request
    ) {
        return jobService.updateChecklist(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable Long id) {
        jobService.deleteJob(CurrentUser.id(), id);
    }
}
