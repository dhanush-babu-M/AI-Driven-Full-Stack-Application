package com.pathpilot.job;

import com.pathpilot.common.ApiException;
import com.pathpilot.job.dto.JobDtos.FollowupRescheduleRequest;
import com.pathpilot.job.dto.JobDtos.JobApplicationDetailDto;
import com.pathpilot.job.dto.JobDtos.JobApplicationRequest;
import com.pathpilot.job.dto.JobDtos.JobChecklistRequest;
import com.pathpilot.job.dto.JobDtos.JobFollowupDto;
import com.pathpilot.job.dto.JobDtos.JobStatsDto;
import com.pathpilot.job.dto.JobDtos.JobStatusChangeRequest;
import com.pathpilot.job.dto.JobDtos.PrepChecklistItemDto;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private JobApplicationRepository jobApplicationRepository;
    private JobStatusHistoryRepository statusHistoryRepository;
    private JobFollowupRepository followupRepository;
    private NotificationPreferencesRepository notificationPreferencesRepository;
    private UserProfileRepository userProfileRepository;
    private JobService jobService;

    private final Map<Long, JobApplication> jobs = new ConcurrentHashMap<>();
    private final List<JobStatusHistory> histories = new ArrayList<>();
    private final Map<Long, JobFollowup> followups = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        jobApplicationRepository = mock(JobApplicationRepository.class);
        statusHistoryRepository = mock(JobStatusHistoryRepository.class);
        followupRepository = mock(JobFollowupRepository.class);
        notificationPreferencesRepository = mock(NotificationPreferencesRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);

        jobService = new JobService(
                jobApplicationRepository,
                statusHistoryRepository,
                followupRepository,
                notificationPreferencesRepository,
                userProfileRepository,
                new ObjectMapper()
        );

        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setTimeZone("UTC");
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(notificationPreferencesRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication app = inv.getArgument(0);
            if (app.getId() == null) app.setId(seq.getAndIncrement());
            app.setCreatedAtUtc(Instant.now());
            app.setUpdatedAtUtc(Instant.now());
            jobs.put(app.getId(), app);
            return app;
        });
        when(jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long uid = inv.getArgument(1);
            JobApplication app = jobs.get(id);
            if (app == null || !app.getUserId().equals(uid) || app.getDeletedAtUtc() != null) {
                return Optional.empty();
            }
            return Optional.of(app);
        });
        when(jobApplicationRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    return jobs.values().stream()
                            .filter(j -> j.getUserId().equals(uid) && j.getDeletedAtUtc() == null)
                            .sorted((a, b) -> b.getAppliedDate().compareTo(a.getAppliedDate()))
                            .toList();
                });

        when(statusHistoryRepository.save(any(JobStatusHistory.class))).thenAnswer(inv -> {
            JobStatusHistory h = inv.getArgument(0);
            if (h.getId() == null) h.setId(seq.getAndIncrement());
            h.setChangedAtUtc(Instant.now());
            histories.add(h);
            return h;
        });
        when(statusHistoryRepository.findAllByUserIdAndJobApplicationIdOrderByChangedAtUtcDescIdDesc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long jid = inv.getArgument(1);
                    return histories.stream()
                            .filter(h -> h.getUserId().equals(uid) && h.getJobApplicationId().equals(jid))
                            .sorted((a, b) -> b.getId().compareTo(a.getId()))
                            .toList();
                });

        when(followupRepository.save(any(JobFollowup.class))).thenAnswer(inv -> {
            JobFollowup f = inv.getArgument(0);
            if (f.getId() == null) f.setId(seq.getAndIncrement());
            followups.put(f.getId(), f);
            return f;
        });
        when(followupRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long uid = inv.getArgument(1);
            JobFollowup f = followups.get(id);
            if (f == null || !f.getUserId().equals(uid) || f.getDeletedAtUtc() != null) {
                return Optional.empty();
            }
            return Optional.of(f);
        });
        when(followupRepository.findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long jid = inv.getArgument(1);
                    return followups.values().stream()
                            .filter(f -> f.getUserId().equals(uid) && f.getJobApplicationId().equals(jid) && f.getDeletedAtUtc() == null)
                            .toList();
                });
        when(followupRepository.findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
                anyLong(), anyString(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    String st = inv.getArgument(1);
                    LocalDate maxDue = inv.getArgument(2);
                    return followups.values().stream()
                            .filter(f -> f.getUserId().equals(uid) && f.getDeletedAtUtc() == null
                                    && st.equalsIgnoreCase(f.getStatus()) && !f.getDueDate().isAfter(maxDue))
                            .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                            .toList();
                });
    }

    @Test
    void statsCalculation_andStatusHistoryWithTimestamps_workAccurately() {
        LocalDate today = LocalDate.now();
        JobApplicationDetailDto app1 = jobService.createJob(1L, new JobApplicationRequest(
                "Acme Labs", "Java Engineer", "APPLIED", today,
                "https://acme.example.com/jobs/1", "<script>alert(1)</script><p>Spring Boot &amp; SQL</p>",
                "Bengaluru", "LINKEDIN", null, today.plusDays(1), null, null, null
        ));
        jobService.createJob(1L, new JobApplicationRequest(
                "Vertex Soft", "Backend Intern", "INTERVIEW", today,
                null, null, "Remote", "COMPANY_SITE", null, null, today.plusDays(3), null, null
        ));
        jobService.createJob(1L, new JobApplicationRequest(
                "Orbit Cloud", "Graduate Dev", "OFFER_RECEIVED", today,
                null, null, "Hyderabad", "REFERRAL", null, null, null, null, null
        ));

        // Plain-text sanitization strips HTML/script tags safely
        assertFalse(app1.jobDescription().contains("<script>"));
        assertTrue(app1.jobDescription().contains("Spring Boot & SQL"));

        JobStatsDto stats = jobService.getStats(1L);
        assertEquals(3, stats.total());
        assertEquals(1, stats.applied());
        assertEquals(1, stats.interviews());
        assertEquals(1, stats.offers());
        assertEquals(0, stats.rejected());
        assertEquals(1, stats.followupsDueThisWeek());

        // Status change records history with timestamp
        JobApplicationDetailDto updated = jobService.updateStatus(
                1L, app1.id(), new JobStatusChangeRequest("INTERVIEW", "Recruiter called")
        );
        assertEquals(2, updated.statusHistory().size());
        assertEquals("INTERVIEW", updated.statusHistory().get(0).newStatus());
        assertEquals("APPLIED", updated.statusHistory().get(0).previousStatus());
        assertNotNull(updated.statusHistory().get(0).changedAtUtc());

        // Checklist editing
        JobApplicationDetailDto withChecklist = jobService.updateChecklist(1L, app1.id(), new JobChecklistRequest(
                List.of(new PrepChecklistItemDto("1", "Revise OOPS", true))
        ));
        assertEquals(1, withChecklist.prepChecklist().size());
        assertTrue(withChecklist.prepChecklist().get(0).done());
    }

    @Test
    void followupWindow_includesOverdueAndNext7Days_andSupportsDoneAndReschedule() {
        LocalDate today = LocalDate.now();
        // Overdue follow-up (applied 5 days ago, follow-up 2 days ago)
        jobService.createJob(1L, new JobApplicationRequest(
                "Overdue Corp", "Backend Dev", "FOLLOW_UP_NEEDED", today.minusDays(5),
                null, null, null, "LINKEDIN", null, today.minusDays(2), null, null, null
        ));
        // Due tomorrow (within 7 days)
        jobService.createJob(1L, new JobApplicationRequest(
                "Tomorrow Corp", "Java Dev", "APPLIED", today,
                null, null, null, "NAUKRI", null, today.plusDays(1), null, null, null
        ));
        // Outside 7-day window (due in 12 days)
        jobService.createJob(1L, new JobApplicationRequest(
                "Later Corp", "SDE-1", "APPLIED", today,
                null, null, null, "OTHER", null, today.plusDays(12), null, null, null
        ));

        List<JobFollowupDto> upcoming = jobService.getUpcomingFollowups(1L);
        assertEquals(2, upcoming.size());
        assertTrue(upcoming.get(0).overdue());
        assertEquals("Overdue Corp", upcoming.get(0).companyName());
        assertFalse(upcoming.get(1).overdue());
        assertEquals("Tomorrow Corp", upcoming.get(1).companyName());
        assertNotNull(upcoming.get(1).reminderAtUtc());

        // Reschedule overdue to +3 days => no longer overdue
        JobFollowupDto rescheduled = jobService.rescheduleFollowup(
                1L, upcoming.get(0).id(), new FollowupRescheduleRequest(today.plusDays(3), "Pushed to Friday")
        );
        assertFalse(rescheduled.overdue());

        // Mark done removes from upcoming
        jobService.markFollowupDone(1L, upcoming.get(1).id());
        assertEquals(1, jobService.getUpcomingFollowups(1L).size());
    }

    @Test
    void userIsolation_andDateAndUrlValidation_areEnforced() {
        LocalDate today = LocalDate.now();
        JobApplicationDetailDto created = jobService.createJob(1L, new JobApplicationRequest(
                "Private Corp", "Engineer", "INTERESTED", today,
                null, null, null, "OTHER", null, null, null, null, null
        ));

        // User 2 cannot read or update User 1's job
        ApiException notFound = assertThrows(ApiException.class, () -> jobService.getJob(2L, created.id()));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());

        // Applied date far in future rejected
        ApiException farFuture = assertThrows(ApiException.class, () -> jobService.createJob(1L, new JobApplicationRequest(
                "Future Co", "Dev", "APPLIED", today.plusDays(30),
                null, null, null, "OTHER", null, null, null, null, null
        )));
        assertEquals(HttpStatus.BAD_REQUEST, farFuture.getStatus());

        // Follow-up before applied date rejected
        ApiException badFollowup = assertThrows(ApiException.class, () -> jobService.createJob(1L, new JobApplicationRequest(
                "Order Co", "Dev", "APPLIED", today,
                null, null, null, "OTHER", null, today.minusDays(1), null, null, null
        )));
        assertEquals(HttpStatus.BAD_REQUEST, badFollowup.getStatus());

        // Invalid job URL rejected
        ApiException badUrl = assertThrows(ApiException.class, () -> jobService.createJob(1L, new JobApplicationRequest(
                "Url Co", "Dev", "APPLIED", today,
                "javascript:alert(1)", null, null, "OTHER", null, null, null, null, null
        )));
        assertEquals(HttpStatus.BAD_REQUEST, badUrl.getStatus());
    }
}
