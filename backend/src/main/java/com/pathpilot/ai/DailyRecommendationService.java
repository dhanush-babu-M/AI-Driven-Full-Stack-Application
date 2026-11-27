package com.pathpilot.ai;

import com.pathpilot.ai.AiStructuredExecutor.ExecutionOutcome;
import com.pathpilot.ai.LlmClient.JsonSchema;
import com.pathpilot.ai.LlmClient.LlmPrompt;
import com.pathpilot.ai.RuleRecommendationEngine.RuleResult;
import com.pathpilot.ai.dto.AiDtos;
import com.pathpilot.ai.dto.AiDtos.DailyFactsDto;
import com.pathpilot.ai.dto.AiDtos.DailyRecommendationResponse;
import com.pathpilot.ai.dto.AiDtos.GoalGapDto;
import com.pathpilot.ai.dto.AiDtos.ProposedTaskDraftDto;
import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.job.JobFollowup;
import com.pathpilot.job.JobFollowupRepository;
import com.pathpilot.planner.GoalService;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskCategory;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.dto.GoalDtos.GoalDto;
import com.pathpilot.progress.ProgressCalculator;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DailyRecommendationService {

    private static final String CACHE_FEATURE = "daily-recommendation";
    private static final long CACHE_TTL_SECONDS = 6 * 3600L;
    private static final List<String> UNFINISHED_STATUSES = List.of("PLANNED", "PENDING");

    private final TaskRepository taskRepository;
    private final CodingActivityRepository codingActivityRepository;
    private final JobFollowupRepository jobFollowupRepository;
    private final GoalService goalService;
    private final UserProfileRepository userProfileRepository;
    private final RuleRecommendationEngine ruleEngine;
    private final AiStructuredExecutor structuredExecutor;
    private final AiCacheService cacheService;
    private final ObjectMapper objectMapper;

    public DailyRecommendationService(
            TaskRepository taskRepository,
            CodingActivityRepository codingActivityRepository,
            JobFollowupRepository jobFollowupRepository,
            GoalService goalService,
            UserProfileRepository userProfileRepository,
            RuleRecommendationEngine ruleEngine,
            AiStructuredExecutor structuredExecutor,
            AiCacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.codingActivityRepository = codingActivityRepository;
        this.jobFollowupRepository = jobFollowupRepository;
        this.goalService = goalService;
        this.userProfileRepository = userProfileRepository;
        this.ruleEngine = ruleEngine;
        this.structuredExecutor = structuredExecutor;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DailyRecommendationResponse getDailyRecommendation(Long userId, LocalDate requestedDate, boolean refresh) {
        String tz = resolveTimeZone(userId);
        LocalDate targetDate = requestedDate != null ? requestedDate : PeriodBoundaryHelper.localToday(Instant.now(), tz);
        String cacheKey = targetDate.toString();

        if (!refresh) {
            var cached = cacheService.get(userId, CACHE_FEATURE, cacheKey, DailyRecommendationResponse.class);
            if (cached.isPresent()) {
                DailyRecommendationResponse hit = cached.get();
                return new DailyRecommendationResponse(
                        hit.text(),
                        hit.source(),
                        hit.disclaimer(),
                        hit.notice(),
                        true,
                        hit.facts(),
                        hit.proposedTask(),
                        hit.actionLabel(),
                        hit.actionPath()
                );
            }
        }

        DailyFactsDto facts = gatherFacts(userId, targetDate);
        RuleResult ruleResult = ruleEngine.evaluate(facts);

        LlmPrompt prompt = buildPrompt(facts, ruleResult);
        JsonSchema schema = buildSchema();

        ExecutionOutcome<LlmRecommendationPayload> outcome = structuredExecutor.execute(
                prompt,
                schema,
                LlmRecommendationPayload.class,
                this::validateLlmPayload
        );

        DailyRecommendationResponse response;
        if (outcome.success() && outcome.data() != null) {
            LlmRecommendationPayload ai = outcome.data();
            ProposedTaskDraftDto proposedTask = ai.proposedTask() != null
                    ? new ProposedTaskDraftDto(
                            ai.proposedTask().title().trim(),
                            ai.proposedTask().reason(),
                            normalizeCategory(ai.proposedTask().category()),
                            targetDate,
                            "MEDIUM"
                    )
                    : ruleResult.proposedTask();

            response = new DailyRecommendationResponse(
                    ai.recommendationText().trim(),
                    "AI",
                    AiDtos.AI_DISCLAIMER,
                    null,
                    false,
                    facts,
                    proposedTask,
                    ruleResult.actionLabel(),
                    ruleResult.actionPath()
            );
        } else {
            response = new DailyRecommendationResponse(
                    ruleResult.text(),
                    "RULE",
                    null,
                    outcome.notice(),
                    false,
                    facts,
                    ruleResult.proposedTask(),
                    ruleResult.actionLabel(),
                    ruleResult.actionPath()
            );
        }

        cacheService.put(userId, CACHE_FEATURE, cacheKey, response, CACHE_TTL_SECONDS);
        return response;
    }

    public DailyFactsDto gatherFacts(Long userId, LocalDate targetDate) {
        List<CodingActivity> codings = codingActivityRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByActivityDateDescIdDesc(userId);
        Integer daysSinceLastCoding = null;
        if (!codings.isEmpty()) {
            LocalDate latest = codings.get(0).getActivityDate();
            long diff = ChronoUnit.DAYS.between(latest, targetDate);
            daysSinceLastCoding = (int) Math.max(0, diff);
        }

        List<Task> overdueTasks = taskRepository
                .findAllByUserIdAndTaskDateBeforeAndStatusInAndDeletedAtUtcIsNullOrderByTaskDateDescPriorityAscIdAsc(
                        userId, targetDate, UNFINISHED_STATUSES
                );
        String topOverdue = overdueTasks.isEmpty() ? null : overdueTasks.get(0).getTitle();

        List<Task> todayTasks = taskRepository
                .findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(userId, targetDate);
        ProgressCalculator.TaskSummary summary = ProgressCalculator.summarizeTasks(
                todayTasks.stream().map(Task::getStatus).toList()
        );

        int upcomingFollowups = jobFollowupRepository
                .findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
                        userId, JobFollowup.STATUS_PENDING, targetDate.plusDays(3)
                )
                .size();

        List<GoalDto> weeklyGoals = goalService.listGoals(userId, "WEEK", targetDate);
        List<GoalGapDto> gaps = weeklyGoals.stream()
                .filter(g -> !"COMPLETED".equalsIgnoreCase(g.status()) && !"RESCHEDULED".equalsIgnoreCase(g.status()))
                .filter(g -> g.targetCount() > g.doneCount())
                .map(g -> new GoalGapDto(
                        g.title(),
                        g.category(),
                        g.targetCount(),
                        g.doneCount(),
                        g.targetCount() - g.doneCount()
                ))
                .toList();

        return new DailyFactsDto(
                targetDate,
                daysSinceLastCoding,
                overdueTasks.size(),
                topOverdue,
                summary.plannedCount(),
                summary.completedCount(),
                summary.pendingCount(),
                upcomingFollowups,
                gaps
        );
    }

    private LlmPrompt buildPrompt(DailyFactsDto facts, RuleResult ruleResult) {
        String system = """
                You are PathPilot's factual career assistant.
                Strict rules:
                - Use ONLY the supplied factual counts and goal names.
                - If data is missing or zero, say so honestly; NEVER invent achievements, hours, or activity.
                - Write 1–2 short, friendly, specific sentences actionable today.
                - Provide one optional proposed task matching an allowed TaskCategory.
                - Return valid JSON matching the schema.
                """;
        String userMsg = "Daily facts JSON (no PII):\n"
                + writeJson(facts)
                + "\n\nDeterministic baseline suggestion:\n"
                + ruleResult.text();
        return new LlmPrompt(system, userMsg);
    }

    private JsonSchema buildSchema() {
        Map<String, Object> proposedTaskSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "category", Map.of("type", "string"),
                        "reason", Map.of("type", "string")
                ),
                "required", List.of("title", "category", "reason")
        );
        Map<String, Object> root = Map.of(
                "type", "object",
                "properties", Map.of(
                        "recommendationText", Map.of("type", "string"),
                        "proposedTask", proposedTaskSchema
                ),
                "required", List.of("recommendationText", "proposedTask")
        );
        return new JsonSchema("daily_recommendation", root);
    }

    private String validateLlmPayload(LlmRecommendationPayload payload) {
        if (payload == null || payload.recommendationText() == null || payload.recommendationText().isBlank()) {
            return "recommendationText must be non-blank.";
        }
        if (payload.proposedTask() != null) {
            if (payload.proposedTask().title() == null || payload.proposedTask().title().isBlank()) {
                return "proposedTask.title must be non-blank.";
            }
        }
        return null;
    }

    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "JAVA_LEARNING";
        try {
            return TaskCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            return "JAVA_LEARNING";
        }
    }

    private String resolveTimeZone(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public record LlmRecommendationPayload(
            String recommendationText,
            LlmProposedTask proposedTask
    ) {
    }

    public record LlmProposedTask(
            String title,
            String category,
            String reason
    ) {
    }
}
