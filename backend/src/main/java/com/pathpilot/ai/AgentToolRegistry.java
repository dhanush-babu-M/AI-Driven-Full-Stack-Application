package com.pathpilot.ai;

import com.pathpilot.ai.dto.AiDtos.AiProposalDto;
import com.pathpilot.ai.dto.AiDtos.CreateTaskProposalRequest;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisRequest;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisResponse;
import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.common.ApiException;
import com.pathpilot.job.JobService;
import com.pathpilot.planner.LearningRecord;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskCategory;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.progress.ProgressCalculator;
import com.pathpilot.project.ProjectWorklog;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, strictly-validated set of read/propose tools callable by Agent Mode.
 * userId ALWAYS comes from CurrentUser.id(); passing userId in tool arguments is forbidden.
 */
@Component
public class AgentToolRegistry {

    public static final Set<String> ALLOWED_TOOLS = Set.of(
            "getRecordedActivity",
            "calculateProgress",
            "findOverdueTasks",
            "analyzeJobDescription",
            "proposeTask"
    );

    private static final Set<String> FORBIDDEN_USER_KEYS = Set.of(
            "userid", "user_id", "uid", "ownerid", "owner_id", "accountid", "account_id"
    );
    private static final Set<String> ALLOWED_RANGES = Set.of("7d", "30d", "today", "week", "month");
    private static final List<String> UNFINISHED_STATUSES = List.of("PLANNED", "PENDING");

    private final CodingActivityRepository codingActivityRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final ProjectWorklogRepository projectWorklogRepository;
    private final TaskRepository taskRepository;
    private final UserProfileRepository userProfileRepository;
    private final JdAnalysisService jdAnalysisService;
    private final AiProposalService aiProposalService;

    public AgentToolRegistry(
            CodingActivityRepository codingActivityRepository,
            LearningRecordRepository learningRecordRepository,
            ProjectWorklogRepository projectWorklogRepository,
            TaskRepository taskRepository,
            UserProfileRepository userProfileRepository,
            JdAnalysisService jdAnalysisService,
            AiProposalService aiProposalService
    ) {
        this.codingActivityRepository = codingActivityRepository;
        this.learningRecordRepository = learningRecordRepository;
        this.projectWorklogRepository = projectWorklogRepository;
        this.taskRepository = taskRepository;
        this.userProfileRepository = userProfileRepository;
        this.jdAnalysisService = jdAnalysisService;
        this.aiProposalService = aiProposalService;
    }

    public void validateToolInput(String toolName, Map<String, Object> rawArgs) {
        if (toolName == null || !ALLOWED_TOOLS.contains(toolName)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TOOL_NAME",
                    "Tool \"" + toolName + "\" is not permitted."
            );
        }

        Map<String, Object> args = rawArgs != null ? rawArgs : Map.of();
        for (String key : args.keySet()) {
            if (key != null && FORBIDDEN_USER_KEYS.contains(key.trim().toLowerCase(Locale.ROOT))) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN_TOOL_ARGUMENT",
                        "Tool calls cannot pass a user id; identity is enforced from the authenticated token."
                );
            }
        }

        switch (toolName) {
            case "getRecordedActivity", "calculateProgress" -> {
                String range = str(args.get("range"), "7d").toLowerCase(Locale.ROOT);
                if (!ALLOWED_RANGES.contains(range)) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_TOOL_ARGUMENT",
                            "range must be one of: today, 7d, week, 30d, month."
                    );
                }
            }
            case "analyzeJobDescription" -> {
                String text = str(args.get("text"), "");
                if (text.isBlank()) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_TOOL_ARGUMENT",
                            "analyzeJobDescription requires non-blank text."
                    );
                }
                if (text.length() > 12000) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_TOOL_ARGUMENT",
                            "Job description text exceeds 12,000 characters."
                    );
                }
            }
            case "proposeTask" -> {
                String title = str(args.get("title"), "");
                if (title.isBlank() || title.length() > 200) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_TOOL_ARGUMENT",
                            "proposeTask requires a title (1–200 characters)."
                    );
                }
                String dateStr = str(args.get("date"), null);
                if (dateStr != null) {
                    try {
                        LocalDate.parse(dateStr);
                    } catch (Exception ex) {
                        throw new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "INVALID_TOOL_ARGUMENT",
                                "proposeTask date must be YYYY-MM-DD."
                        );
                    }
                }
                String cat = str(args.get("category"), "JAVA_LEARNING");
                try {
                    TaskCategory.valueOf(cat.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_TOOL_ARGUMENT",
                            "Invalid task category: " + cat
                    );
                }
            }
            default -> {
                // findOverdueTasks requires no parameters
            }
        }
    }

    public ToolExecutionResult execute(Long authenticatedUserId, String toolName, Map<String, Object> rawArgs) {
        validateToolInput(toolName, rawArgs);
        Map<String, Object> args = rawArgs != null ? rawArgs : Map.of();
        LocalDate today = localToday(authenticatedUserId);

        return switch (toolName) {
            case "getRecordedActivity" -> {
                String range = str(args.get("range"), "7d").toLowerCase(Locale.ROOT);
                LocalDate start = resolveRangeStart(today, range);
                List<CodingActivity> codings = codingActivityRepository
                        .findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                                authenticatedUserId, start, today
                        );
                List<LearningRecord> learnings = learningRecordRepository
                        .findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(authenticatedUserId, start, today);
                List<ProjectWorklog> worklogs = projectWorklogRepository
                        .findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                                authenticatedUserId, start, today
                        );

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("from", start.toString());
                data.put("to", today.toString());
                data.put("codingEntriesCount", codings.size());
                data.put("codingDaysCount", codings.stream().map(CodingActivity::getActivityDate).distinct().count());
                data.put("learningRecordsCount", learnings.size());
                data.put("projectWorklogsCount", worklogs.size());

                String summary = "Activity (" + start + " to " + today + "): " + codings.size()
                        + " coding entry(ies), " + learnings.size() + " learning record(s), "
                        + worklogs.size() + " project work log(s).";
                yield new ToolExecutionResult(data, summary, null);
            }
            case "calculateProgress" -> {
                String range = str(args.get("range"), "today").toLowerCase(Locale.ROOT);
                LocalDate start = resolveRangeStart(today, range);
                List<Task> tasks = taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
                        authenticatedUserId, start, today
                );
                ProgressCalculator.TaskSummary summaryStats = ProgressCalculator.summarizeTasks(
                        tasks.stream().map(Task::getStatus).toList()
                );
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("from", start.toString());
                data.put("to", today.toString());
                data.put("plannedCount", summaryStats.plannedCount());
                data.put("completedCount", summaryStats.completedCount());
                data.put("pendingCount", summaryStats.pendingCount());
                data.put("completionRate", summaryStats.completionRate());

                String summary = "Task progress (" + range + "): " + summaryStats.completedCount()
                        + "/" + summaryStats.plannedCount() + " completed"
                        + (summaryStats.completionRate() != null ? " (" + summaryStats.completionRate() + "%)" : "") + ".";
                yield new ToolExecutionResult(data, summary, null);
            }
            case "findOverdueTasks" -> {
                List<Task> overdue = taskRepository
                        .findAllByUserIdAndTaskDateBeforeAndStatusInAndDeletedAtUtcIsNullOrderByTaskDateDescPriorityAscIdAsc(
                                authenticatedUserId, today, UNFINISHED_STATUSES
                        );
                List<Map<String, Object>> items = overdue.stream()
                        .limit(5)
                        .map(t -> Map.<String, Object>of(
                                "id", t.getId(),
                                "title", t.getTitle(),
                                "taskDate", t.getTaskDate().toString(),
                                "category", t.getCategory()
                        ))
                        .toList();
                Map<String, Object> data = Map.of("count", overdue.size(), "topTasks", items);
                String summary = "Found " + overdue.size() + " unfinished task(s) before " + today + ".";
                yield new ToolExecutionResult(data, summary, null);
            }
            case "analyzeJobDescription" -> {
                String text = JobService.sanitizePlainText(str(args.get("text"), ""));
                JdAnalysisResponse analysis = jdAnalysisService.analyze(
                        authenticatedUserId, new JdAnalysisRequest(null, text)
                );
                Map<String, Object> data = Map.of(
                        "source", analysis.source(),
                        "matchedSkills", analysis.matchedSkills(),
                        "gaps", analysis.gaps(),
                        "weakAreas", analysis.weakAreas(),
                        "recommendedProjects", analysis.recommendedProjectsToShow()
                );
                String summary = "Analyzed JD (" + analysis.source() + "): matched "
                        + analysis.matchedSkills().size() + " skill(s), found "
                        + analysis.gaps().size() + " gap(s).";
                yield new ToolExecutionResult(data, summary, null);
            }
            case "proposeTask" -> {
                String title = str(args.get("title"), "");
                LocalDate date = args.get("date") != null ? LocalDate.parse(str(args.get("date"), today.toString())) : today;
                String category = str(args.get("category"), "JAVA_LEARNING").toUpperCase(Locale.ROOT);
                AiProposalDto created = aiProposalService.createTaskProposal(
                        authenticatedUserId,
                        new CreateTaskProposalRequest(title, "Proposed by PathPilot Career Agent", category, date, "MEDIUM")
                );
                String summary = "Created AI proposal #" + created.id() + " (\"" + created.title() + "\") awaiting user approval.";
                yield new ToolExecutionResult(Map.of("proposalId", created.id(), "status", created.status()), summary, created);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOOL_NAME", "Unknown tool.");
        };
    }

    private LocalDate resolveRangeStart(LocalDate today, String range) {
        return switch (range) {
            case "today" -> today;
            case "7d" -> today.minusDays(6);
            case "week" -> PeriodBoundaryHelper.weekRangeContaining(today).start();
            case "30d" -> today.minusDays(29);
            case "month" -> today.withDayOfMonth(1);
            default -> today.minusDays(6);
        };
    }

    private LocalDate localToday(Long userId) {
        String tz = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(z -> z != null && !z.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
        return PeriodBoundaryHelper.localToday(Instant.now(), tz);
    }

    private String str(Object obj, String fallback) {
        if (obj == null) return fallback;
        String s = String.valueOf(obj).trim();
        return s.isEmpty() ? fallback : s;
    }

    public record ToolExecutionResult(
            Map<String, Object> data,
            String summary,
            AiProposalDto createdProposal
    ) {
    }
}
