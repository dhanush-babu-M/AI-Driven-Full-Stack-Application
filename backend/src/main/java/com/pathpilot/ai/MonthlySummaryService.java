package com.pathpilot.ai;

import com.pathpilot.ai.AiStructuredExecutor.ExecutionOutcome;
import com.pathpilot.ai.LlmClient.JsonSchema;
import com.pathpilot.ai.LlmClient.LlmPrompt;
import com.pathpilot.ai.MonthlySummaryGrounder.MonthlySummaryPayload;
import com.pathpilot.ai.dto.AiDtos;
import com.pathpilot.ai.dto.AiDtos.MonthlySummaryResponse;
import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.common.ApiException;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.GoalService;
import com.pathpilot.planner.LearningRecord;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.dto.GoalDtos.GoalDto;
import com.pathpilot.progress.ProgressCalculator;
import com.pathpilot.project.ProjectMilestone;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectWorklog;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.report.MonthlyReport;
import com.pathpilot.report.MonthlyReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MonthlySummaryService {

    private static final String CACHE_FEATURE = "monthly-summary";
    private static final long CACHE_TTL_SECONDS = 12 * 3600L;

    private final TaskRepository taskRepository;
    private final CodingActivityRepository codingActivityRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final ProjectWorklogRepository projectWorklogRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final GoalService goalService;
    private final MonthlyReportRepository monthlyReportRepository;
    private final MonthlySummaryGrounder grounder;
    private final AiStructuredExecutor structuredExecutor;
    private final AiCacheService cacheService;
    private final ObjectMapper objectMapper;

    public MonthlySummaryService(
            TaskRepository taskRepository,
            CodingActivityRepository codingActivityRepository,
            LearningRecordRepository learningRecordRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            ProjectWorklogRepository projectWorklogRepository,
            JobApplicationRepository jobApplicationRepository,
            GoalService goalService,
            MonthlyReportRepository monthlyReportRepository,
            MonthlySummaryGrounder grounder,
            AiStructuredExecutor structuredExecutor,
            AiCacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.codingActivityRepository = codingActivityRepository;
        this.learningRecordRepository = learningRecordRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.projectWorklogRepository = projectWorklogRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.goalService = goalService;
        this.monthlyReportRepository = monthlyReportRepository;
        this.grounder = grounder;
        this.structuredExecutor = structuredExecutor;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MonthlySummaryResponse generateMonthlySummary(Long userId, String monthStr, boolean refresh) {
        YearMonth month;
        try {
            month = YearMonth.parse(monthStr);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Month must be in yyyy-MM format.");
        }

        if (!refresh) {
            var cached = cacheService.get(userId, CACHE_FEATURE, month.toString(), MonthlySummaryResponse.class);
            if (cached.isPresent()) {
                MonthlySummaryResponse hit = cached.get();
                return new MonthlySummaryResponse(
                        hit.month(), hit.source(), hit.disclaimer(), hit.notice(), true,
                        hit.summaryParagraphs(), hit.wentWell(), hit.unfinished(),
                        hit.nextMonthPriorities(), hit.generatedAtUtc()
                );
            }
        }

        PeriodBoundaryHelper.DateRange range = PeriodBoundaryHelper.monthRange(month);
        LocalDate start = range.start();
        LocalDate end = range.end();
        int daysInMonth = month.lengthOfMonth();

        List<Task> tasks = taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(userId, start, end);
        ProgressCalculator.TaskSummary taskSummary = ProgressCalculator.summarizeTasks(
                tasks.stream().map(Task::getStatus).toList()
        );

        List<CodingActivity> codings = codingActivityRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(userId, start, end);
        Set<LocalDate> codingDates = codings.stream().map(CodingActivity::getActivityDate).collect(Collectors.toSet());
        int codingMinutes = codings.stream().mapToInt(CodingActivity::getMinutesSpent).sum();
        Double codingRate = ProgressCalculator.calculateCodingActivityRate(codingDates, daysInMonth);

        List<LearningRecord> learnings = learningRecordRepository
                .findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(userId, start, end);
        int learningDays = (int) learnings.stream().map(LearningRecord::getRecordDate).distinct().count();
        int learningMinutes = learnings.stream().mapToInt(LearningRecord::getMinutesSpent).sum();
        List<String> learningTopics = learnings.stream().map(LearningRecord::getTopic).distinct().limit(10).toList();

        List<ProjectMilestone> milestones = projectMilestoneRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndDueDateBetweenOrderByDueDateAscIdAsc(userId, start, end);
        int milestonesCompleted = (int) milestones.stream()
                .filter(m -> ProgressCalculator.STATUS_COMPLETED.equalsIgnoreCase(m.getStatus()))
                .count();

        List<ProjectWorklog> worklogs = projectWorklogRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(userId, start, end);
        int worklogMinutes = worklogs.stream().mapToInt(ProjectWorklog::getMinutesSpent).sum();

        List<GoalDto> goals = goalService.listGoals(userId, "MONTH", start);
        int goalsDone = (int) goals.stream().filter(g -> g.doneCount() >= g.targetCount()).count();
        List<String> unfinishedGoalTitles = goals.stream()
                .filter(g -> g.doneCount() < g.targetCount())
                .map(g -> g.title() + " (" + g.doneCount() + "/" + g.targetCount() + ")")
                .toList();

        int applicationsCount = jobApplicationRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndAppliedDateBetween(userId, start, end)
                .size();

        Set<String> allowedNumbers = grounder.collectSnapshotNumbers(
                month.getYear(), month.getMonthValue(),
                taskSummary.plannedCount(), taskSummary.completedCount(),
                taskSummary.pendingCount(), taskSummary.skippedCount(),
                taskSummary.completionRate(),
                codingDates.size(), codings.size(), codingMinutes, codingRate,
                learningDays, learningMinutes,
                worklogs.size(), worklogMinutes,
                milestonesCompleted, milestones.size(),
                goalsDone, goals.size(),
                applicationsCount
        );

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("month", month.toString());
        snapshot.put("plannedTasks", taskSummary.plannedCount());
        snapshot.put("completedTasks", taskSummary.completedCount());
        snapshot.put("pendingTasks", taskSummary.pendingCount());
        snapshot.put("skippedTasks", taskSummary.skippedCount());
        snapshot.put("taskCompletionRate", taskSummary.completionRate());
        snapshot.put("codingDays", codingDates.size());
        snapshot.put("codingEntries", codings.size());
        snapshot.put("codingMinutes", codingMinutes);
        snapshot.put("learningDays", learningDays);
        snapshot.put("learningMinutes", learningMinutes);
        snapshot.put("learningTopics", learningTopics);
        snapshot.put("projectWorklogEntries", worklogs.size());
        snapshot.put("projectWorklogMinutes", worklogMinutes);
        snapshot.put("milestonesCompleted", milestonesCompleted);
        snapshot.put("milestonesTotal", milestones.size());
        snapshot.put("goalsCompleted", goalsDone);
        snapshot.put("goalsTotal", goals.size());
        snapshot.put("unfinishedGoals", unfinishedGoalTitles);
        snapshot.put("jobApplicationsCount", applicationsCount);

        LlmPrompt prompt = buildPrompt(snapshot);
        JsonSchema schema = buildSchema();

        ExecutionOutcome<MonthlySummaryPayload> outcome = structuredExecutor.execute(
                prompt,
                schema,
                MonthlySummaryPayload.class,
                payload -> grounder.validate(payload, allowedNumbers)
        );

        Instant now = Instant.now();
        MonthlySummaryResponse response;
        if (outcome.success() && outcome.data() != null) {
            MonthlySummaryPayload ai = outcome.data();
            response = new MonthlySummaryResponse(
                    month.toString(),
                    "AI",
                    AiDtos.AI_DISCLAIMER,
                    null,
                    false,
                    safeList(ai.summaryParagraphs()),
                    safeList(ai.wentWell()),
                    safeList(ai.unfinished()),
                    safeList(ai.nextMonthPriorities()),
                    now
            );
        } else {
            MonthlySummaryPayload rulePayload = buildRuleSummary(
                    month.toString(),
                    taskSummary,
                    codingDates.size(),
                    learningDays,
                    worklogs.size(),
                    milestonesCompleted,
                    milestones.size(),
                    unfinishedGoalTitles,
                    applicationsCount
            );
            response = new MonthlySummaryResponse(
                    month.toString(),
                    "RULE",
                    "Rule-based summary generated strictly from your saved monthly records.",
                    outcome.notice(),
                    false,
                    rulePayload.summaryParagraphs(),
                    rulePayload.wentWell(),
                    rulePayload.unfinished(),
                    rulePayload.nextMonthPriorities(),
                    now
            );
        }

        persistReportSnapshot(
                userId, month.toString(), taskSummary, codingDates.size(), codingRate, learningDays, response, now
        );
        cacheService.put(userId, CACHE_FEATURE, month.toString(), response, CACHE_TTL_SECONDS);
        return response;
    }

    private MonthlySummaryPayload buildRuleSummary(
            String month,
            ProgressCalculator.TaskSummary taskSummary,
            int codingDays,
            int learningDays,
            int worklogsCount,
            int milestonesCompleted,
            int milestonesTotal,
            List<String> unfinishedGoals,
            int applicationsCount
    ) {
        List<String> paragraphs = new ArrayList<>();
        if (taskSummary.plannedCount() > 0) {
            paragraphs.add("In " + month + ", you completed " + taskSummary.completedCount() + " of "
                    + taskSummary.plannedCount() + " planned tasks (" + taskSummary.completionRate() + "%).");
        } else {
            paragraphs.add("In " + month + ", no tasks were planned yet.");
        }
        paragraphs.add("You logged coding practice on " + codingDays + " day(s), learning records on "
                + learningDays + " day(s), " + worklogsCount + " project work log(s), and "
                + applicationsCount + " job application(s).");

        List<String> wentWell = new ArrayList<>();
        if (taskSummary.completedCount() > 0) {
            wentWell.add("Completed " + taskSummary.completedCount() + " planned task(s).");
        }
        if (codingDays > 0) {
            wentWell.add("Recorded coding activity on " + codingDays + " day(s).");
        }
        if (milestonesCompleted > 0) {
            wentWell.add("Finished " + milestonesCompleted + " of " + milestonesTotal + " project milestone(s).");
        }
        if (wentWell.isEmpty()) {
            wentWell.add("Started tracking " + month + " in PathPilot.");
        }

        List<String> unfinished = new ArrayList<>();
        if (taskSummary.pendingCount() > 0) {
            unfinished.add(taskSummary.pendingCount() + " planned task(s) remain pending.");
        }
        unfinished.addAll(unfinishedGoals);
        if (unfinished.isEmpty()) {
            unfinished.add("No unfinished tasks or goals recorded for " + month + ".");
        }

        List<String> priorities = List.of(
                "Plan focused weekly goals for next month before the first Monday.",
                "Record coding practice and project work logs consistently as you work."
        );

        return new MonthlySummaryPayload(paragraphs, wentWell, unfinished, priorities);
    }

    private void persistReportSnapshot(
            Long userId,
            String reportMonth,
            ProgressCalculator.TaskSummary taskSummary,
            int codingDays,
            Double codingRate,
            int learningDays,
            MonthlySummaryResponse response,
            Instant now
    ) {
        MonthlyReport report = monthlyReportRepository.findByUserIdAndReportMonth(userId, reportMonth)
                .orElseGet(() -> {
                    MonthlyReport created = new MonthlyReport();
                    created.setUserId(userId);
                    created.setReportMonth(reportMonth);
                    return created;
                });
        report.setPlannedTasks(taskSummary.plannedCount());
        report.setCompletedTasks(taskSummary.completedCount());
        report.setCodingDays(codingDays);
        report.setLearningDays(learningDays);
        report.setTaskCompletionRate(taskSummary.completionRate() != null
                ? BigDecimal.valueOf(taskSummary.completionRate())
                : BigDecimal.ZERO);
        report.setCodingActivityRate(codingRate != null
                ? BigDecimal.valueOf(codingRate)
                : BigDecimal.ZERO);
        report.setAiSummaryJson(writeJson(response));
        report.setAiSummaryGeneratedAtUtc(now);
        monthlyReportRepository.save(report);
    }

    private LlmPrompt buildPrompt(Map<String, Object> snapshot) {
        String system = """
                You are PathPilot's monthly progress reviewer.
                STRICT GROUNDING RULES:
                - Every single number you mention MUST appear in the provided monthly snapshot JSON.
                - NEVER invent hours, counts, percentages, or achievements not present in the snapshot.
                - Each wentWell and unfinished item should cite a real number or item from the snapshot.
                - Return valid JSON matching the schema.
                """;
        return new LlmPrompt(system, "Monthly snapshot JSON (no PII):\n" + writeJson(snapshot));
    }

    private JsonSchema buildSchema() {
        Map<String, Object> strArray = Map.of("type", "array", "items", Map.of("type", "string"));
        Map<String, Object> root = Map.of(
                "type", "object",
                "properties", Map.of(
                        "summaryParagraphs", strArray,
                        "wentWell", strArray,
                        "unfinished", strArray,
                        "nextMonthPriorities", strArray
                ),
                "required", List.of("summaryParagraphs", "wentWell", "unfinished", "nextMonthPriorities")
        );
        return new JsonSchema("monthly_summary", root);
    }

    private List<String> safeList(List<String> list) {
        return list == null ? List.of() : list.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
