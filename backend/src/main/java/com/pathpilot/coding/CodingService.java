package com.pathpilot.coding;

import com.pathpilot.coding.dto.CodingDtos.ChartPointDto;
import com.pathpilot.coding.dto.CodingDtos.CodingActivityDto;
import com.pathpilot.coding.dto.CodingDtos.CodingActivityRequest;
import com.pathpilot.coding.dto.CodingDtos.CodingPageResponse;
import com.pathpilot.coding.dto.CodingDtos.CodingStreakSummaryResponse;
import com.pathpilot.coding.dto.CodingDtos.DayActivityDetailResponse;
import com.pathpilot.coding.dto.CodingDtos.DayGithubEventDto;
import com.pathpilot.coding.dto.CodingDtos.DayLearningRecordDto;
import com.pathpilot.coding.dto.CodingDtos.DayProjectWorklogDto;
import com.pathpilot.coding.dto.CodingDtos.HeatmapDayDto;
import com.pathpilot.coding.dto.CodingDtos.HeatmapResponse;
import com.pathpilot.coding.dto.CodingDtos.HeatmapTotalsDto;
import com.pathpilot.coding.dto.CodingDtos.InactivePeriodDto;
import com.pathpilot.coding.dto.CodingDtos.RuleBannerDto;
import com.pathpilot.common.ApiException;
import com.pathpilot.github.GithubEvent;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.planner.LearningRecord;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.dto.RecordHistoryDto;
import com.pathpilot.progress.ProgressCalculator;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.project.ProjectWorklog;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class CodingService {

    public static final String ENTITY_TYPE = "CODING_ACTIVITY";
    private static final Set<String> ALLOWED_RESULTS = Set.of("Solved", "Attempted", "Practiced");
    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("Easy", "Medium", "Hard");
    private static final DateTimeFormatter SHORT_DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");

    private final CodingActivityRepository codingActivityRepository;
    private final GithubEventRepository githubEventRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final ProjectWorklogRepository projectWorklogRepository;
    private final ProjectRepository projectRepository;
    private final RecordHistoryRepository recordHistoryRepository;
    private final UserProfileRepository userProfileRepository;

    public CodingService(
            CodingActivityRepository codingActivityRepository,
            GithubEventRepository githubEventRepository,
            LearningRecordRepository learningRecordRepository,
            ProjectWorklogRepository projectWorklogRepository,
            ProjectRepository projectRepository,
            RecordHistoryRepository recordHistoryRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.codingActivityRepository = codingActivityRepository;
        this.githubEventRepository = githubEventRepository;
        this.learningRecordRepository = learningRecordRepository;
        this.projectWorklogRepository = projectWorklogRepository;
        this.projectRepository = projectRepository;
        this.recordHistoryRepository = recordHistoryRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public CodingPageResponse listActivities(
            Long userId,
            LocalDate from,
            LocalDate to,
            String platform,
            String topic,
            String result,
            int page,
            int size
    ) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "End date cannot be before start date.");
        }

        List<CodingActivity> inRange = codingActivityRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        userId, start, end
                );

        Set<String> availableTopics = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CodingActivity item : inRange) {
            if (item.getTopic() != null && !item.getTopic().isBlank()) {
                availableTopics.add(item.getTopic().trim());
            }
        }

        String normalizedPlatform = trimToNull(platform);
        String normalizedTopic = trimToNull(topic);
        String normalizedResult = trimToNull(result);

        List<CodingActivityDto> filtered = inRange.stream()
                .filter(a -> matchesPlatform(a.getPlatform(), normalizedPlatform))
                .filter(a -> normalizedTopic == null || normalizedTopic.equalsIgnoreCase(String.valueOf(a.getTopic())))
                .filter(a -> normalizedResult == null || normalizedResult.equalsIgnoreCase(a.getResult()))
                .map(this::toDto)
                .toList();

        int safeSize = size <= 0 ? 10 : Math.min(size, 500);
        int safePage = Math.max(0, page);
        int totalItems = filtered.size();
        int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / safeSize);
        int fromIdx = Math.min(safePage * safeSize, totalItems);
        int toIdx = Math.min(fromIdx + safeSize, totalItems);

        return new CodingPageResponse(
                filtered.subList(fromIdx, toIdx),
                safePage,
                safeSize,
                totalItems,
                totalPages,
                new ArrayList<>(availableTopics)
        );
    }

    @Transactional
    public CodingActivityDto create(Long userId, CodingActivityRequest request) {
        String normalizedResult = normalizeResult(request.result());
        String normalizedDifficulty = normalizeDifficulty(request.difficulty());

        CodingActivity activity = new CodingActivity();
        activity.setUserId(userId);
        activity.setActivityDate(request.activityDate());
        activity.setPlatform(normalizePlatform(request.platform()));
        activity.setProblemTitle(request.problemTitle().trim());
        activity.setTopic(trimToNull(request.topic()));
        activity.setDifficulty(normalizedDifficulty);
        activity.setResult(normalizedResult);
        activity.setProblemsSolved(resolveProblemsSolved(request.problemsSolved(), normalizedResult));
        activity.setMinutesSpent(request.minutesSpent() != null ? request.minutesSpent() : 0);
        activity.setReferenceUrl(trimToNull(request.referenceUrl()));
        activity.setNotes(trimToNull(request.notes()));
        activity.setDataSource("MANUAL");

        CodingActivity saved = codingActivityRepository.save(activity);
        recordHistory(userId, saved.getId(), "CREATE", saved.getActivityDate(),
                "Logged practice: " + snapshotActivity(saved));
        return toDto(saved);
    }

    @Transactional
    public CodingActivityDto update(Long userId, Long id, CodingActivityRequest request) {
        CodingActivity activity = codingActivityRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODING_NOT_FOUND", "Coding practice entry not found."));

        String before = snapshotActivity(activity);
        String normalizedResult = normalizeResult(request.result());
        String normalizedDifficulty = normalizeDifficulty(request.difficulty());

        activity.setActivityDate(request.activityDate());
        activity.setPlatform(normalizePlatform(request.platform()));
        activity.setProblemTitle(request.problemTitle().trim());
        activity.setTopic(trimToNull(request.topic()));
        activity.setDifficulty(normalizedDifficulty);
        activity.setResult(normalizedResult);
        activity.setProblemsSolved(resolveProblemsSolved(request.problemsSolved(), normalizedResult));
        if (request.minutesSpent() != null) {
            activity.setMinutesSpent(request.minutesSpent());
        }
        activity.setReferenceUrl(trimToNull(request.referenceUrl()));
        activity.setNotes(trimToNull(request.notes()));
        if (!"DEMO".equalsIgnoreCase(activity.getDataSource())) {
            activity.setDataSource("MANUAL");
        }

        CodingActivity saved = codingActivityRepository.save(activity);
        recordHistory(userId, saved.getId(), "UPDATE", saved.getActivityDate(),
                "Updated from " + before + " to " + snapshotActivity(saved));
        return toDto(saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        CodingActivity activity = codingActivityRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODING_NOT_FOUND", "Coding practice entry not found."));

        activity.setDeletedAtUtc(Instant.now());
        codingActivityRepository.save(activity);
        recordHistory(userId, activity.getId(), "DELETE", activity.getActivityDate(),
                "Deleted practice: " + snapshotActivity(activity));
    }

    @Transactional(readOnly = true)
    public List<RecordHistoryDto> getHistory(Long userId, Long id) {
        // Allow viewing history even after soft delete, strictly scoped by userId.
        List<RecordHistory> entries = recordHistoryRepository
                .findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(userId, ENTITY_TYPE, id);
        return entries.stream()
                .map(h -> new RecordHistoryDto(
                        h.getId(),
                        h.getEntityType(),
                        h.getEntityId(),
                        h.getActionType(),
                        h.getActorSource(),
                        h.getRecordDate(),
                        h.getChangeSummary(),
                        h.getCreatedAtUtc()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmap(Long userId, LocalDate from, LocalDate to, String source) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "End date cannot be before start date.");
        }

        String normalizedSource = normalizeHeatmapSource(source);

        List<GithubEvent> events = "manual".equals(normalizedSource)
                ? List.of()
                : githubEventRepository.findAllByUserIdAndEventDateBetween(userId, start, end).stream()
                        .filter(e -> "IMPORTED".equalsIgnoreCase(e.getDataSource()) || "DEMO".equalsIgnoreCase(e.getDataSource()))
                        .toList();

        List<CodingActivity> manualEntries = "github".equals(normalizedSource)
                ? List.of()
                : codingActivityRepository
                        .findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(userId, start, end)
                        .stream()
                        .filter(a -> "MANUAL".equalsIgnoreCase(a.getDataSource()))
                        .toList();

        Map<LocalDate, Integer> githubEventCounts = new HashMap<>();
        Map<LocalDate, Integer> githubCommitCounts = new HashMap<>();
        for (GithubEvent event : events) {
            githubEventCounts.merge(event.getEventDate(), 1, Integer::sum);
            int commits = event.getCommitCount() > 0 ? event.getCommitCount()
                    : ("PushEvent".equalsIgnoreCase(event.getEventType()) ? 1 : 0);
            githubCommitCounts.merge(event.getEventDate(), commits, Integer::sum);
        }

        Map<LocalDate, Integer> manualCounts = new HashMap<>();
        Map<LocalDate, Integer> manualSolvedCounts = new HashMap<>();
        for (CodingActivity entry : manualEntries) {
            manualCounts.merge(entry.getActivityDate(), 1, Integer::sum);
            if ("Solved".equalsIgnoreCase(entry.getResult())) {
                manualSolvedCounts.merge(entry.getActivityDate(), Math.max(1, entry.getProblemsSolved()), Integer::sum);
            }
        }

        List<HeatmapDayDto> days = new ArrayList<>();
        int totalGithubEvents = 0;
        int totalGithubCommits = 0;
        int totalManualEntries = 0;
        int totalManualSolved = 0;
        int activeDays = 0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            int ghEvents = githubEventCounts.getOrDefault(d, 0);
            int ghCommits = githubCommitCounts.getOrDefault(d, 0);
            int manCount = manualCounts.getOrDefault(d, 0);
            int manSolved = manualSolvedCounts.getOrDefault(d, 0);

            int ghSignal = Math.max(ghEvents, ghCommits);
            int displayCount = switch (normalizedSource) {
                case "github" -> ghSignal;
                case "manual" -> manCount;
                default -> ghSignal + manCount;
            };

            if (displayCount > 0) {
                activeDays++;
            }
            totalGithubEvents += ghEvents;
            totalGithubCommits += ghCommits;
            totalManualEntries += manCount;
            totalManualSolved += manSolved;

            String category;
            if (ghEvents > 0 && manCount > 0) {
                category = "BOTH";
            } else if (ghEvents > 0) {
                category = "GITHUB_ONLY";
            } else if (manCount > 0) {
                category = "MANUAL_ONLY";
            } else {
                category = "NONE";
            }

            days.add(new HeatmapDayDto(
                    d,
                    ghEvents,
                    ghCommits,
                    manCount,
                    manSolved,
                    displayCount,
                    toHeatmapLevel(displayCount),
                    category
            ));
        }

        return new HeatmapResponse(
                start,
                end,
                normalizedSource,
                days,
                new HeatmapTotalsDto(
                        totalGithubEvents,
                        totalGithubCommits,
                        totalManualEntries,
                        totalManualSolved,
                        activeDays
                )
        );
    }

    @Transactional(readOnly = true)
    public DayActivityDetailResponse getDayDetails(Long userId, LocalDate date) {
        List<DayGithubEventDto> githubDtos = githubEventRepository
                .findAllByUserIdAndEventDateOrderByOccurredAtUtcDescIdDesc(userId, date)
                .stream()
                .map(e -> new DayGithubEventDto(
                        e.getId(),
                        e.getExternalEventId(),
                        e.getEventType(),
                        e.getRepoName(),
                        e.getCommitCount(),
                        e.getPayloadSummary(),
                        e.getHtmlUrl(),
                        e.getOccurredAtUtc(),
                        e.getDataSource()
                ))
                .toList();

        List<CodingActivityDto> codingDtos = codingActivityRepository
                .findAllByUserIdAndActivityDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcAscIdAsc(userId, date)
                .stream()
                .map(this::toDto)
                .toList();

        List<DayLearningRecordDto> learningDtos = learningRecordRepository
                .findAllByUserIdAndRecordDateOrderByCreatedAtUtcAscIdAsc(userId, date)
                .stream()
                .map(lr -> new DayLearningRecordDto(
                        lr.getId(),
                        lr.getTopic(),
                        lr.getMinutesSpent(),
                        lr.getNotes(),
                        lr.getDataSource()
                ))
                .toList();

        List<ProjectWorklog> worklogs = projectWorklogRepository
                .findAllByUserIdAndWorkDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcDescIdDesc(userId, date);
        Map<Long, String> projectTitles = projectRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(userId)
                .stream()
                .collect(Collectors.toMap(Project::getId, Project::getTitle, (a, b) -> a));

        List<DayProjectWorklogDto> worklogDtos = worklogs.stream()
                .map(w -> new DayProjectWorklogDto(
                        w.getId(),
                        w.getProjectId(),
                        projectTitles.getOrDefault(w.getProjectId(), "Project #" + w.getProjectId()),
                        w.getMinutesSpent(),
                        w.getSummary(),
                        w.getDataSource()
                ))
                .toList();

        return new DayActivityDetailResponse(date, githubDtos, codingDtos, learningDtos, worklogDtos);
    }

    @Transactional(readOnly = true)
    public CodingStreakSummaryResponse getStreakAndSummary(Long userId, LocalDate from, LocalDate to) {
        String tz = resolveTimeZone(userId);
        LocalDate today = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "End date cannot be before start date.");
        }

        List<CodingActivity> allManual = codingActivityRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByActivityDateDescIdDesc(userId);

        Set<LocalDate> allDates = allManual.stream()
                .map(CodingActivity::getActivityDate)
                .collect(Collectors.toSet());

        int currentStreak = ProgressCalculator.calculateCodingStreak(allDates, today);
        int longestStreak = ProgressCalculator.calculateLongestCodingStreak(allDates);
        LocalDate lastPracticeDate = allManual.isEmpty() ? null : allManual.get(0).getActivityDate();

        List<CodingActivity> periodEntries = allManual.stream()
                .filter(a -> !a.getActivityDate().isBefore(start) && !a.getActivityDate().isAfter(end))
                .toList();

        int totalSolved = 0;
        int totalAttempted = 0;
        int totalPracticed = 0;
        Set<LocalDate> periodActiveDates = new TreeSet<>();
        for (CodingActivity entry : periodEntries) {
            periodActiveDates.add(entry.getActivityDate());
            String res = entry.getResult() == null ? "Solved" : entry.getResult();
            if ("Solved".equalsIgnoreCase(res)) {
                totalSolved++;
            } else if ("Attempted".equalsIgnoreCase(res)) {
                totalAttempted++;
            } else {
                totalPracticed++;
            }
        }

        int daysInPeriod = (int) ChronoUnit.DAYS.between(start, end) + 1;
        Double practicePercent = ProgressCalculator.calculateCodingActivityRate(periodActiveDates, daysInPeriod);

        // Only evaluate inactive periods up to today so future days in a month are not treated as gaps.
        LocalDate inactiveTo = end.isAfter(today) ? today : end;
        List<InactivePeriodDto> inactivePeriods = ProgressCalculator
                .findInactivePeriods(start, inactiveTo, periodActiveDates)
                .stream()
                .map(p -> new InactivePeriodDto(p.startDate(), p.endDate(), p.days(), "no record"))
                .toList();

        List<ChartPointDto> chartSeries = buildChartSeries(start, end, periodEntries);
        RuleBannerDto banner = buildNeutralRuleBanner(
                periodEntries.size(),
                totalSolved,
                periodActiveDates.size(),
                currentStreak
        );

        return new CodingStreakSummaryResponse(
                start,
                end,
                daysInPeriod,
                periodEntries.size(),
                totalSolved,
                totalAttempted,
                totalPracticed,
                periodActiveDates.size(),
                practicePercent,
                currentStreak,
                longestStreak,
                lastPracticeDate,
                inactivePeriods,
                chartSeries,
                banner
        );
    }

    private List<ChartPointDto> buildChartSeries(LocalDate start, LocalDate end, List<CodingActivity> entries) {
        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        boolean useWeeklyBuckets = totalDays > 35;

        if (!useWeeklyBuckets) {
            Map<LocalDate, int[]> counts = new LinkedHashMap<>();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                counts.put(d, new int[3]);
            }
            for (CodingActivity e : entries) {
                int[] c = counts.get(e.getActivityDate());
                if (c != null) {
                    incrementResultBucket(c, e.getResult());
                }
            }
            List<ChartPointDto> points = new ArrayList<>();
            for (Map.Entry<LocalDate, int[]> entry : counts.entrySet()) {
                int[] c = entry.getValue();
                points.add(new ChartPointDto(
                        entry.getKey().format(SHORT_DAY_LABEL),
                        entry.getKey(),
                        c[0], c[1], c[2],
                        c[0] + c[1] + c[2]
                ));
            }
            return points;
        }

        // Group into Monday-aligned weeks for longer ranges (90d / 1y)
        Map<LocalDate, int[]> weekly = new LinkedHashMap<>();
        LocalDate cursor = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!cursor.isAfter(end)) {
            weekly.put(cursor, new int[3]);
            cursor = cursor.plusWeeks(1);
        }
        for (CodingActivity e : entries) {
            LocalDate weekStart = e.getActivityDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int[] c = weekly.get(weekStart);
            if (c != null) {
                incrementResultBucket(c, e.getResult());
            }
        }
        List<ChartPointDto> points = new ArrayList<>();
        for (Map.Entry<LocalDate, int[]> entry : weekly.entrySet()) {
            int[] c = entry.getValue();
            points.add(new ChartPointDto(
                    entry.getKey().format(SHORT_DAY_LABEL),
                    entry.getKey(),
                    c[0], c[1], c[2],
                    c[0] + c[1] + c[2]
            ));
        }
        return points;
    }

    private void incrementResultBucket(int[] counts, String result) {
        if ("Solved".equalsIgnoreCase(result)) {
            counts[0]++;
        } else if ("Attempted".equalsIgnoreCase(result)) {
            counts[1]++;
        } else {
            counts[2]++;
        }
    }

    private RuleBannerDto buildNeutralRuleBanner(
            int totalEntries,
            int totalSolved,
            int practiceDays,
            int currentStreak
    ) {
        if (totalEntries == 0) {
            return new RuleBannerDto(
                    "No practice entries recorded in this range yet. Log a problem whenever you practice to build an accurate record.",
                    "RULE"
            );
        }
        if (currentStreak >= 3) {
            return new RuleBannerDto(
                    "Recorded practice on " + currentStreak + " consecutive day(s). Your active days and problem notes are logged.",
                    "RULE"
            );
        }
        return new RuleBannerDto(
                "Logged " + totalEntries + " practice entry(ies) across " + practiceDays
                        + " day(s) in this period (" + totalSolved + " marked Solved).",
                "RULE"
        );
    }

    private void recordHistory(
            Long userId,
            Long entityId,
            String actionType,
            LocalDate recordDate,
            String summary
    ) {
        RecordHistory history = new RecordHistory();
        history.setUserId(userId);
        history.setEntityType(ENTITY_TYPE);
        history.setEntityId(entityId);
        history.setActionType(actionType);
        history.setActorSource("MANUAL");
        history.setRecordDate(recordDate);
        history.setChangeSummary(summary);
        recordHistoryRepository.save(history);
    }

    private String snapshotActivity(CodingActivity a) {
        return "[platform=" + a.getPlatform()
                + ", problem=\"" + a.getProblemTitle()
                + "\", topic=" + (a.getTopic() != null ? a.getTopic() : "-")
                + ", difficulty=" + (a.getDifficulty() != null ? a.getDifficulty() : "-")
                + ", result=" + a.getResult()
                + ", date=" + a.getActivityDate()
                + "]";
    }

    private boolean matchesPlatform(String rowPlatform, String filterPlatform) {
        if (filterPlatform == null) {
            return true;
        }
        if ("Other".equalsIgnoreCase(filterPlatform)) {
            return !"LeetCode".equalsIgnoreCase(rowPlatform) && !"CodingBat".equalsIgnoreCase(rowPlatform);
        }
        return filterPlatform.equalsIgnoreCase(rowPlatform);
    }

    private String normalizePlatform(String raw) {
        String trimmed = raw.trim();
        if ("leetcode".equalsIgnoreCase(trimmed)) return "LeetCode";
        if ("codingbat".equalsIgnoreCase(trimmed)) return "CodingBat";
        if ("hackerrank".equalsIgnoreCase(trimmed)) return "HackerRank";
        return trimmed;
    }

    private String normalizeResult(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        for (String allowed : ALLOWED_RESULTS) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid result value.",
                Map.of("result", "Result must be Solved, Attempted, or Practiced.")
        );
    }

    private String normalizeDifficulty(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        for (String allowed : ALLOWED_DIFFICULTIES) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid difficulty value.",
                Map.of("difficulty", "Difficulty must be Easy, Medium, or Hard.")
        );
    }

    private int resolveProblemsSolved(Integer requested, String result) {
        if (requested != null) {
            return requested;
        }
        return "Solved".equalsIgnoreCase(result) ? 1 : 0;
    }

    private String normalizeHeatmapSource(String source) {
        if (source == null || source.isBlank()) {
            return "combined";
        }
        String lower = source.trim().toLowerCase(Locale.ROOT);
        if (Set.of("github", "manual", "combined").contains(lower)) {
            return lower;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid heatmap source.",
                Map.of("source", "Source must be github, manual, or combined.")
        );
    }

    private int toHeatmapLevel(int count) {
        if (count <= 0) return 0;
        if (count == 1) return 1;
        if (count <= 3) return 2;
        if (count <= 6) return 3;
        return 4;
    }

    private String resolveTimeZone(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public CodingActivityDto toDto(CodingActivity a) {
        return new CodingActivityDto(
                a.getId(),
                a.getActivityDate(),
                a.getPlatform(),
                a.getProblemTitle(),
                a.getTopic(),
                a.getDifficulty(),
                a.getResult(),
                a.getProblemsSolved(),
                a.getMinutesSpent(),
                a.getReferenceUrl(),
                a.getNotes(),
                a.getDataSource(),
                a.getCreatedAtUtc(),
                a.getUpdatedAtUtc()
        );
    }
}
