package com.pathpilot.job;

import com.pathpilot.common.ApiException;
import com.pathpilot.common.UrlValidationUtil;
import com.pathpilot.job.dto.JobDtos.FollowupRescheduleRequest;
import com.pathpilot.job.dto.JobDtos.JobApplicationDetailDto;
import com.pathpilot.job.dto.JobDtos.JobApplicationRequest;
import com.pathpilot.job.dto.JobDtos.JobApplicationSummaryDto;
import com.pathpilot.job.dto.JobDtos.JobChecklistRequest;
import com.pathpilot.job.dto.JobDtos.JobFollowupDto;
import com.pathpilot.job.dto.JobDtos.JobPageResponse;
import com.pathpilot.job.dto.JobDtos.JobStatsDto;
import com.pathpilot.job.dto.JobDtos.JobStatusChangeRequest;
import com.pathpilot.job.dto.JobDtos.JobStatusHistoryDto;
import com.pathpilot.job.dto.JobDtos.PrepChecklistItemDto;
import com.pathpilot.planner.NotificationPreferences;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.ReminderTimeCalculator;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class JobService {

    public static final Set<String> ALLOWED_STATUSES = Set.of(
            "INTERESTED",
            "TO_APPLY",
            "APPLIED",
            "FOLLOW_UP_NEEDED",
            "ASSESSMENT",
            "INTERVIEW",
            "REJECTED",
            "OFFER_RECEIVED"
    );

    public static final Set<String> ALLOWED_SOURCES = Set.of(
            "LINKEDIN",
            "NAUKRI",
            "COMPANY_SITE",
            "REFERRAL",
            "OTHER"
    );

    private static final int MAX_FUTURE_APPLIED_DAYS = 7;
    private static final int FOLLOWUP_WINDOW_DAYS = 7;
    private static final Pattern SCRIPT_OR_STYLE_PATTERN =
            Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final JobApplicationRepository jobApplicationRepository;
    private final JobStatusHistoryRepository statusHistoryRepository;
    private final JobFollowupRepository followupRepository;
    private final NotificationPreferencesRepository notificationPreferencesRepository;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    public JobService(
            JobApplicationRepository jobApplicationRepository,
            JobStatusHistoryRepository statusHistoryRepository,
            JobFollowupRepository followupRepository,
            NotificationPreferencesRepository notificationPreferencesRepository,
            UserProfileRepository userProfileRepository,
            ObjectMapper objectMapper
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.followupRepository = followupRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
        this.userProfileRepository = userProfileRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public JobPageResponse listJobs(
            Long userId,
            String search,
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "End date cannot be before start date.");
        }

        String normalizedSearch = UrlValidationUtil.trimToNull(search);
        String normalizedStatus = UrlValidationUtil.trimToNull(status);
        String statusFilter = normalizedStatus != null ? normalizeStatus(normalizedStatus, null) : null;

        List<JobApplicationSummaryDto> filtered = jobApplicationRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(userId)
                .stream()
                .filter(app -> statusFilter == null || statusFilter.equalsIgnoreCase(normalizeStatusSafe(app.getStatus())))
                .filter(app -> from == null || !app.getAppliedDate().isBefore(from))
                .filter(app -> to == null || !app.getAppliedDate().isAfter(to))
                .filter(app -> matchesSearch(app, normalizedSearch))
                .map(this::toSummaryDto)
                .toList();

        int safeSize = size <= 0 ? 10 : Math.min(size, 500);
        int safePage = Math.max(0, page);
        int totalItems = filtered.size();
        int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / safeSize);
        int fromIdx = Math.min(safePage * safeSize, totalItems);
        int toIdx = Math.min(fromIdx + safeSize, totalItems);

        return new JobPageResponse(filtered.subList(fromIdx, toIdx), safePage, safeSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public JobApplicationDetailDto getJob(Long userId, Long jobId) {
        JobApplication app = requireOwnedJob(userId, jobId);
        return buildDetailDto(userId, app);
    }

    @Transactional
    public JobApplicationDetailDto createJob(Long userId, JobApplicationRequest request) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);

        String status = normalizeStatus(request.status(), "APPLIED");
        String source = normalizeSource(request.source());
        String jobUrl = UrlValidationUtil.validateOptionalHttpUrl(request.jobUrl(), "jobUrl");
        validateJobDates(today, request.appliedDate(), request.followupDate(), request.interviewDate());

        JobApplication app = new JobApplication();
        app.setUserId(userId);
        app.setCompanyName(sanitizePlainText(request.companyName()));
        app.setRoleTitle(sanitizePlainText(request.roleTitle()));
        app.setStatus(status);
        app.setAppliedDate(request.appliedDate());
        app.setJobUrl(jobUrl);
        app.setJobDescription(sanitizePlainText(request.jobDescription()));
        app.setLocation(sanitizePlainText(request.location()));
        app.setSource(source);
        app.setRecruiterNotes(sanitizePlainText(request.recruiterNotes()));
        app.setFollowupDate(request.followupDate());
        app.setInterviewDate(request.interviewDate());
        app.setNotes(sanitizePlainText(request.notes()));
        app.setPrepChecklistJson(writeChecklistJson(normalizeChecklist(request.prepChecklist())));
        app.setDataSource("MANUAL");

        JobApplication saved = jobApplicationRepository.save(app);

        recordStatusHistory(userId, saved.getId(), null, status, today, "Application created");

        if (saved.getFollowupDate() != null) {
            upsertPendingFollowup(userId, saved, saved.getFollowupDate(), "Follow up with " + saved.getCompanyName(), tz);
        }

        return buildDetailDto(userId, saved);
    }

    @Transactional
    public JobApplicationDetailDto updateJob(Long userId, Long jobId, JobApplicationRequest request) {
        JobApplication app = requireOwnedJob(userId, jobId);
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);

        String previousStatus = normalizeStatusSafe(app.getStatus());
        String nextStatus = normalizeStatus(request.status(), previousStatus);
        String source = normalizeSource(request.source());
        String jobUrl = UrlValidationUtil.validateOptionalHttpUrl(request.jobUrl(), "jobUrl");
        validateJobDates(today, request.appliedDate(), request.followupDate(), request.interviewDate());

        app.setCompanyName(sanitizePlainText(request.companyName()));
        app.setRoleTitle(sanitizePlainText(request.roleTitle()));
        app.setStatus(nextStatus);
        app.setAppliedDate(request.appliedDate());
        app.setJobUrl(jobUrl);
        app.setJobDescription(sanitizePlainText(request.jobDescription()));
        app.setLocation(sanitizePlainText(request.location()));
        app.setSource(source);
        app.setRecruiterNotes(sanitizePlainText(request.recruiterNotes()));
        app.setFollowupDate(request.followupDate());
        app.setInterviewDate(request.interviewDate());
        app.setNotes(sanitizePlainText(request.notes()));
        if (request.prepChecklist() != null) {
            app.setPrepChecklistJson(writeChecklistJson(normalizeChecklist(request.prepChecklist())));
        }
        if (!"DEMO".equalsIgnoreCase(app.getDataSource())) {
            app.setDataSource("MANUAL");
        }

        JobApplication saved = jobApplicationRepository.save(app);

        if (!previousStatus.equals(nextStatus)) {
            recordStatusHistory(userId, saved.getId(), previousStatus, nextStatus, today, "Updated application status");
        }

        if (saved.getFollowupDate() != null) {
            upsertPendingFollowup(userId, saved, saved.getFollowupDate(), "Follow up with " + saved.getCompanyName(), tz);
        } else {
            clearPendingFollowupsForJob(userId, saved.getId());
        }

        return buildDetailDto(userId, saved);
    }

    @Transactional
    public JobApplicationDetailDto updateStatus(Long userId, Long jobId, JobStatusChangeRequest request) {
        JobApplication app = requireOwnedJob(userId, jobId);
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);

        String previousStatus = normalizeStatusSafe(app.getStatus());
        String nextStatus = normalizeStatus(request.status(), previousStatus);

        app.setStatus(nextStatus);
        JobApplication saved = jobApplicationRepository.save(app);

        if (!previousStatus.equals(nextStatus) || UrlValidationUtil.trimToNull(request.note()) != null) {
            recordStatusHistory(
                    userId,
                    saved.getId(),
                    previousStatus,
                    nextStatus,
                    today,
                    sanitizePlainText(request.note())
            );
        }

        return buildDetailDto(userId, saved);
    }

    @Transactional
    public JobApplicationDetailDto updateChecklist(Long userId, Long jobId, JobChecklistRequest request) {
        JobApplication app = requireOwnedJob(userId, jobId);
        List<PrepChecklistItemDto> cleaned = normalizeChecklist(request.items());
        app.setPrepChecklistJson(writeChecklistJson(cleaned));
        JobApplication saved = jobApplicationRepository.save(app);
        return buildDetailDto(userId, saved);
    }

    @Transactional
    public void deleteJob(Long userId, Long jobId) {
        JobApplication app = requireOwnedJob(userId, jobId);
        Instant now = Instant.now();
        app.setDeletedAtUtc(now);
        jobApplicationRepository.save(app);

        List<JobFollowup> followups = followupRepository
                .findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(userId, jobId);
        for (JobFollowup f : followups) {
            f.setDeletedAtUtc(now);
            followupRepository.save(f);
        }
    }

    @Transactional(readOnly = true)
    public JobStatsDto getStats(Long userId) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        LocalDate windowEnd = today.plusDays(FOLLOWUP_WINDOW_DAYS);

        List<JobApplication> apps = jobApplicationRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(userId);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (String st : ALLOWED_STATUSES) {
            statusCounts.put(st, 0L);
        }
        for (JobApplication app : apps) {
            String st = normalizeStatusSafe(app.getStatus());
            statusCounts.merge(st, 1L, Long::sum);
        }

        long followupsDue = followupRepository
                .findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
                        userId, JobFollowup.STATUS_PENDING, windowEnd
                )
                .size();

        return new JobStatsDto(
                apps.size(),
                statusCounts.get("INTERESTED"),
                statusCounts.get("TO_APPLY"),
                statusCounts.get("APPLIED"),
                statusCounts.get("FOLLOW_UP_NEEDED"),
                statusCounts.get("ASSESSMENT"),
                statusCounts.get("INTERVIEW"),
                statusCounts.get("OFFER_RECEIVED"),
                statusCounts.get("REJECTED"),
                followupsDue,
                statusCounts
        );
    }

    @Transactional(readOnly = true)
    public List<JobFollowupDto> getUpcomingFollowups(Long userId) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        LocalDate windowEnd = today.plusDays(FOLLOWUP_WINDOW_DAYS);

        List<JobFollowup> followups = followupRepository
                .findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
                        userId, JobFollowup.STATUS_PENDING, windowEnd
                );

        if (followups.isEmpty()) {
            return List.of();
        }

        Map<Long, JobApplication> jobsById = jobApplicationRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(userId)
                .stream()
                .collect(Collectors.toMap(JobApplication::getId, a -> a, (a, b) -> a));

        return followups.stream()
                .filter(f -> jobsById.containsKey(f.getJobApplicationId()))
                .map(f -> toFollowupDto(f, jobsById.get(f.getJobApplicationId()), today))
                .toList();
    }

    @Transactional
    public JobFollowupDto markFollowupDone(Long userId, Long followupId) {
        JobFollowup followup = requireOwnedFollowup(userId, followupId);
        JobApplication app = requireOwnedJob(userId, followup.getJobApplicationId());
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);

        followup.setStatus(JobFollowup.STATUS_DONE);
        followup.setCompletedAtUtc(Instant.now());
        JobFollowup saved = followupRepository.save(followup);

        syncJobFollowupDate(userId, app);
        return toFollowupDto(saved, app, today);
    }

    @Transactional
    public JobFollowupDto rescheduleFollowup(Long userId, Long followupId, FollowupRescheduleRequest request) {
        JobFollowup followup = requireOwnedFollowup(userId, followupId);
        JobApplication app = requireOwnedJob(userId, followup.getJobApplicationId());
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);

        if (app.getAppliedDate() != null && request.dueDate().isBefore(app.getAppliedDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Follow-up date cannot be before applied date.",
                    Map.of("dueDate", "Follow-up date cannot be before " + app.getAppliedDate() + ".")
            );
        }

        followup.setDueDate(request.dueDate());
        followup.setStatus(JobFollowup.STATUS_PENDING);
        followup.setCompletedAtUtc(null);
        if (UrlValidationUtil.trimToNull(request.note()) != null) {
            followup.setNote(sanitizePlainText(request.note()));
        }
        int leadMinutes = resolveLeadMinutes(userId);
        followup.setReminderAtUtc(ReminderTimeCalculator.computeReminderAtUtc(
                request.dueDate(), followup.getReminderTime(), leadMinutes, tz
        ));
        JobFollowup saved = followupRepository.save(followup);

        app.setFollowupDate(request.dueDate());
        jobApplicationRepository.save(app);

        return toFollowupDto(saved, app, today);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void upsertPendingFollowup(Long userId, JobApplication app, LocalDate dueDate, String defaultNote, String tz) {
        List<JobFollowup> existing = followupRepository
                .findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(userId, app.getId());

        JobFollowup target = existing.stream()
                .filter(f -> JobFollowup.STATUS_PENDING.equalsIgnoreCase(f.getStatus()))
                .findFirst()
                .orElseGet(() -> {
                    JobFollowup f = new JobFollowup();
                    f.setUserId(userId);
                    f.setJobApplicationId(app.getId());
                    f.setStatus(JobFollowup.STATUS_PENDING);
                    f.setReminderTime(JobFollowup.DEFAULT_REMINDER_TIME);
                    f.setNote(defaultNote);
                    return f;
                });

        target.setDueDate(dueDate);
        if (target.getReminderTime() == null) {
            target.setReminderTime(JobFollowup.DEFAULT_REMINDER_TIME);
        }
        int leadMinutes = resolveLeadMinutes(userId);
        target.setReminderAtUtc(ReminderTimeCalculator.computeReminderAtUtc(
                dueDate, target.getReminderTime(), leadMinutes, tz
        ));
        followupRepository.save(target);
    }

    private void clearPendingFollowupsForJob(Long userId, Long jobId) {
        Instant now = Instant.now();
        List<JobFollowup> existing = followupRepository
                .findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(userId, jobId);
        for (JobFollowup f : existing) {
            if (JobFollowup.STATUS_PENDING.equalsIgnoreCase(f.getStatus())) {
                f.setDeletedAtUtc(now);
                followupRepository.save(f);
            }
        }
    }

    private void syncJobFollowupDate(Long userId, JobApplication app) {
        LocalDate nextPendingDate = followupRepository
                .findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(userId, app.getId())
                .stream()
                .filter(f -> JobFollowup.STATUS_PENDING.equalsIgnoreCase(f.getStatus()))
                .map(JobFollowup::getDueDate)
                .findFirst()
                .orElse(null);
        app.setFollowupDate(nextPendingDate);
        jobApplicationRepository.save(app);
    }

    private void recordStatusHistory(
            Long userId,
            Long jobId,
            String previousStatus,
            String newStatus,
            LocalDate changedDate,
            String note
    ) {
        JobStatusHistory h = new JobStatusHistory();
        h.setUserId(userId);
        h.setJobApplicationId(jobId);
        h.setPreviousStatus(previousStatus);
        h.setNewStatus(newStatus);
        h.setChangedDate(changedDate);
        h.setNote(note);
        statusHistoryRepository.save(h);
    }

    private void validateJobDates(LocalDate today, LocalDate appliedDate, LocalDate followupDate, LocalDate interviewDate) {
        if (appliedDate.isAfter(today.plusDays(MAX_FUTURE_APPLIED_DAYS))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Applied date cannot be in the far future.",
                    Map.of("appliedDate", "Applied date cannot be more than " + MAX_FUTURE_APPLIED_DAYS + " days in the future.")
            );
        }
        if (followupDate != null && followupDate.isBefore(appliedDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Follow-up date cannot be before applied date.",
                    Map.of("followupDate", "Follow-up date cannot be before " + appliedDate + ".")
            );
        }
        if (interviewDate != null && interviewDate.isBefore(appliedDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Interview date cannot be before applied date.",
                    Map.of("interviewDate", "Interview date cannot be before " + appliedDate + ".")
            );
        }
    }

    private JobApplicationDetailDto buildDetailDto(Long userId, JobApplication app) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);

        List<JobStatusHistoryDto> history = statusHistoryRepository
                .findAllByUserIdAndJobApplicationIdOrderByChangedAtUtcDescIdDesc(userId, app.getId())
                .stream()
                .map(h -> new JobStatusHistoryDto(
                        h.getId(),
                        h.getPreviousStatus(),
                        h.getNewStatus(),
                        h.getChangedDate(),
                        h.getNote(),
                        h.getChangedAtUtc()
                ))
                .toList();

        List<JobFollowupDto> followups = followupRepository
                .findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(userId, app.getId())
                .stream()
                .map(f -> toFollowupDto(f, app, today))
                .toList();

        return new JobApplicationDetailDto(
                app.getId(),
                app.getCompanyName(),
                app.getRoleTitle(),
                normalizeStatusSafe(app.getStatus()),
                app.getAppliedDate(),
                app.getJobUrl(),
                app.getJobDescription(),
                app.getLocation(),
                normalizeSourceSafe(app.getSource()),
                app.getRecruiterNotes(),
                app.getFollowupDate(),
                app.getInterviewDate(),
                app.getNotes(),
                readChecklistJson(app.getPrepChecklistJson()),
                app.getDataSource(),
                app.getCreatedAtUtc(),
                app.getUpdatedAtUtc(),
                history,
                followups
        );
    }

    private JobApplicationSummaryDto toSummaryDto(JobApplication app) {
        return new JobApplicationSummaryDto(
                app.getId(),
                app.getCompanyName(),
                app.getRoleTitle(),
                normalizeStatusSafe(app.getStatus()),
                app.getAppliedDate(),
                app.getJobUrl(),
                app.getLocation(),
                normalizeSourceSafe(app.getSource()),
                app.getFollowupDate(),
                app.getInterviewDate(),
                app.getDataSource(),
                app.getCreatedAtUtc(),
                app.getUpdatedAtUtc()
        );
    }

    private JobFollowupDto toFollowupDto(JobFollowup f, JobApplication app, LocalDate today) {
        boolean overdue = JobFollowup.STATUS_PENDING.equalsIgnoreCase(f.getStatus())
                && f.getDueDate() != null
                && f.getDueDate().isBefore(today);
        return new JobFollowupDto(
                f.getId(),
                f.getJobApplicationId(),
                app.getCompanyName(),
                app.getRoleTitle(),
                normalizeStatusSafe(app.getStatus()),
                f.getDueDate(),
                overdue,
                f.getNote(),
                f.getStatus(),
                f.getCompletedAtUtc(),
                f.getReminderAtUtc()
        );
    }

    private JobApplication requireOwnedJob(Long userId, Long jobId) {
        return jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(jobId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job application not found."));
    }

    private JobFollowup requireOwnedFollowup(Long userId, Long followupId) {
        return followupRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(followupId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FOLLOWUP_NOT_FOUND", "Follow-up not found."));
    }

    private boolean matchesSearch(JobApplication app, String query) {
        if (query == null) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(app.getCompanyName(), q)
                || containsIgnoreCase(app.getRoleTitle(), q)
                || containsIgnoreCase(app.getLocation(), q)
                || containsIgnoreCase(app.getSource(), q)
                || containsIgnoreCase(app.getNotes(), q);
    }

    private boolean containsIgnoreCase(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private String normalizeStatus(String raw, String fallback) {
        String trimmed = UrlValidationUtil.trimToNull(raw);
        if (trimmed == null) {
            return fallback;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!ALLOWED_STATUSES.contains(upper)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Invalid job application status.",
                    Map.of("status", "Unsupported status: " + raw)
            );
        }
        return upper;
    }

    public static String normalizeStatusSafe(String raw) {
        if (raw == null || raw.isBlank()) return "APPLIED";
        String upper = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ALLOWED_STATUSES.contains(upper) ? upper : "APPLIED";
    }

    private String normalizeSource(String raw) {
        String trimmed = UrlValidationUtil.trimToNull(raw);
        if (trimmed == null) return "OTHER";
        String upper = trimmed.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("COMPANY".equals(upper) || "COMPANY_WEBSITE".equals(upper)) {
            return "COMPANY_SITE";
        }
        if (!ALLOWED_SOURCES.contains(upper)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Invalid application source.",
                    Map.of("source", "Source must be LinkedIn, Naukri, Company Site, Referral, or Other.")
            );
        }
        return upper;
    }

    public static String normalizeSourceSafe(String raw) {
        if (raw == null || raw.isBlank()) return "OTHER";
        String upper = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ALLOWED_SOURCES.contains(upper) ? upper : "OTHER";
    }

    public static String sanitizePlainText(String raw) {
        if (raw == null) return null;
        String noScripts = SCRIPT_OR_STYLE_PATTERN.matcher(raw).replaceAll("");
        String noTags = HTML_TAG_PATTERN.matcher(noScripts).replaceAll(" ");
        String unescaped = noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        // Strip non-printable control chars except newline and tab
        StringBuilder sb = new StringBuilder(unescaped.length());
        for (int i = 0; i < unescaped.length(); i++) {
            char c = unescaped.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        String trimmed = sb.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<PrepChecklistItemDto> normalizeChecklist(List<PrepChecklistItemDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<PrepChecklistItemDto> result = new ArrayList<>();
        for (PrepChecklistItemDto item : raw) {
            if (item == null || item.text() == null || item.text().isBlank()) continue;
            String id = (item.id() != null && !item.id().isBlank())
                    ? item.id().trim()
                    : UUID.randomUUID().toString();
            String text = sanitizePlainText(item.text());
            if (text != null) {
                result.add(new PrepChecklistItemDto(id, text, item.done()));
            }
        }
        return result;
    }

    private List<PrepChecklistItemDto> readChecklistJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeChecklistJson(List<PrepChecklistItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR", "Unable to save checklist.");
        }
    }

    private String resolveTimeZone(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
    }

    private int resolveLeadMinutes(Long userId) {
        return notificationPreferencesRepository.findByUserId(userId)
                .map(NotificationPreferences::getLeadMinutes)
                .orElse(10);
    }
}
