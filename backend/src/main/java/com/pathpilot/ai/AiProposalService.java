package com.pathpilot.ai;

import com.pathpilot.ai.dto.AiDtos.AiProposalDto;
import com.pathpilot.ai.dto.AiDtos.CreateBatchTaskProposalsRequest;
import com.pathpilot.ai.dto.AiDtos.CreateTaskProposalRequest;
import com.pathpilot.common.ApiException;
import com.pathpilot.planner.DailyLog;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskCategory;
import com.pathpilot.planner.TaskPriority;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.TaskStatus;
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

/**
 * Manages AI proposals in ai_proposals. AI can ONLY propose; user approval creates real records.
 */
@Service
public class AiProposalService {

    private final AiProposalRepository proposalRepository;
    private final TaskRepository taskRepository;
    private final DailyLogRepository dailyLogRepository;
    private final RecordHistoryRepository recordHistoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final AiCacheService cacheService;
    private final ObjectMapper objectMapper;

    public AiProposalService(
            AiProposalRepository proposalRepository,
            TaskRepository taskRepository,
            DailyLogRepository dailyLogRepository,
            RecordHistoryRepository recordHistoryRepository,
            UserProfileRepository userProfileRepository,
            AiCacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.proposalRepository = proposalRepository;
        this.taskRepository = taskRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.recordHistoryRepository = recordHistoryRepository;
        this.userProfileRepository = userProfileRepository;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiProposalDto createTaskProposal(Long userId, CreateTaskProposalRequest request) {
        LocalDate today = localToday(userId);
        LocalDate taskDate = request.taskDate() != null ? request.taskDate() : today;
        TaskCategory category = parseCategorySafe(request.category());
        TaskPriority priority = parsePrioritySafe(request.priority());
        String description = trimToNull(request.description());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", request.title().trim());
        payload.put("description", description);
        payload.put("category", category.name());
        payload.put("taskDate", taskDate.toString());
        payload.put("priority", priority.name());

        AiProposal proposal = new AiProposal();
        proposal.setUserId(userId);
        proposal.setTargetEntity("TASK");
        proposal.setProposalType("CREATE_TASK");
        proposal.setTitle(request.title().trim());
        proposal.setProposedPayload(writeJson(payload));
        proposal.setStatus(AiProposal.STATUS_PENDING);
        proposal.setDataSource("AI");
        proposal.setProposalDate(today);

        AiProposal saved = proposalRepository.save(proposal);
        return toDto(saved);
    }

    @Transactional
    public List<AiProposalDto> createBatchTaskProposals(Long userId, CreateBatchTaskProposalsRequest request) {
        List<AiProposalDto> created = new ArrayList<>();
        for (CreateTaskProposalRequest item : request.tasks()) {
            created.add(createTaskProposal(userId, item));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<AiProposalDto> listProposals(Long userId, String status) {
        List<AiProposal> rows = (status == null || status.isBlank())
                ? proposalRepository.findAllByUserIdOrderByCreatedAtUtcDescIdDesc(userId)
                : proposalRepository.findAllByUserIdAndStatusOrderByCreatedAtUtcDescIdDesc(
                        userId, status.trim().toUpperCase(Locale.ROOT)
                );
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public AiProposalDto approveProposal(Long userId, Long proposalId) {
        AiProposal proposal = requireOwnedProposal(userId, proposalId);
        if (!AiProposal.STATUS_PENDING.equalsIgnoreCase(proposal.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_REVIEWED",
                    "This proposal has already been " + proposal.getStatus().toLowerCase(Locale.ROOT) + "."
            );
        }

        Map<String, Object> payload = readJson(proposal.getProposedPayload());
        String title = str(payload.get("title"), proposal.getTitle());
        String description = trimToNull(str(payload.get("description"), null));
        TaskCategory category = parseCategorySafe(str(payload.get("category"), "JAVA_LEARNING"));
        TaskPriority priority = parsePrioritySafe(str(payload.get("priority"), "MEDIUM"));
        LocalDate taskDate = parseDateSafe(str(payload.get("taskDate"), null), localToday(userId));

        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(title);
        task.setDescription(description);
        task.setTaskDate(taskDate);
        task.setCategory(category.name());
        task.setPriority(priority.getRank());
        task.setStatus(TaskStatus.PLANNED.name());
        task.setDataSource("AI");
        Task savedTask = taskRepository.save(task);

        ensureDayRecorded(userId, taskDate);

        RecordHistory taskHistory = new RecordHistory();
        taskHistory.setUserId(userId);
        taskHistory.setEntityType("TASK");
        taskHistory.setEntityId(savedTask.getId());
        taskHistory.setActionType("CREATED_FROM_AI_PROPOSAL");
        taskHistory.setActorSource("AI");
        taskHistory.setRecordDate(taskDate);
        taskHistory.setChangeSummary("Approved AI proposal #" + proposal.getId() + ": \"" + savedTask.getTitle() + "\"");
        recordHistoryRepository.save(taskHistory);

        proposal.setStatus(AiProposal.STATUS_APPROVED);
        proposal.setReviewedAtUtc(Instant.now());
        AiProposal savedProposal = proposalRepository.save(proposal);

        RecordHistory propHistory = new RecordHistory();
        propHistory.setUserId(userId);
        propHistory.setEntityType("AI_PROPOSAL");
        propHistory.setEntityId(savedProposal.getId());
        propHistory.setActionType("APPROVED");
        propHistory.setActorSource("MANUAL");
        propHistory.setRecordDate(taskDate);
        propHistory.setChangeSummary("Approved proposal \"" + savedProposal.getTitle() + "\" -> created Task #" + savedTask.getId());
        recordHistoryRepository.save(propHistory);

        cacheService.evictUser(userId);
        return toDto(savedProposal);
    }

    @Transactional
    public AiProposalDto rejectProposal(Long userId, Long proposalId) {
        AiProposal proposal = requireOwnedProposal(userId, proposalId);
        if (!AiProposal.STATUS_PENDING.equalsIgnoreCase(proposal.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_REVIEWED",
                    "This proposal has already been " + proposal.getStatus().toLowerCase(Locale.ROOT) + "."
            );
        }

        proposal.setStatus(AiProposal.STATUS_REJECTED);
        proposal.setReviewedAtUtc(Instant.now());
        AiProposal saved = proposalRepository.save(proposal);

        RecordHistory propHistory = new RecordHistory();
        propHistory.setUserId(userId);
        propHistory.setEntityType("AI_PROPOSAL");
        propHistory.setEntityId(saved.getId());
        propHistory.setActionType("REJECTED");
        propHistory.setActorSource("MANUAL");
        propHistory.setRecordDate(localToday(userId));
        propHistory.setChangeSummary("Rejected AI proposal \"" + saved.getTitle() + "\"");
        recordHistoryRepository.save(propHistory);

        return toDto(saved);
    }

    private AiProposal requireOwnedProposal(Long userId, Long proposalId) {
        return proposalRepository.findByIdAndUserId(proposalId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "AI proposal not found."));
    }

    private void ensureDayRecorded(Long userId, LocalDate date) {
        if (dailyLogRepository.findByUserIdAndLogDate(userId, date).isEmpty()) {
            DailyLog log = new DailyLog();
            log.setUserId(userId);
            log.setLogDate(date);
            log.setDayStatus("RECORDED");
            log.setDataSource("AI");
            dailyLogRepository.save(log);
        }
    }

    private AiProposalDto toDto(AiProposal p) {
        Map<String, Object> payload = readJson(p.getProposedPayload());
        return new AiProposalDto(
                p.getId(),
                p.getTargetEntity(),
                p.getProposalType(),
                p.getTitle(),
                str(payload.get("category"), "JAVA_LEARNING"),
                parseDateSafe(str(payload.get("taskDate"), null), p.getProposalDate()),
                str(payload.get("description"), null),
                str(payload.get("priority"), "MEDIUM"),
                p.getStatus(),
                p.getDataSource(),
                p.getProposalDate(),
                p.getCreatedAtUtc(),
                p.getReviewedAtUtc()
        );
    }

    private TaskCategory parseCategorySafe(String raw) {
        if (raw == null || raw.isBlank()) return TaskCategory.JAVA_LEARNING;
        try {
            return TaskCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TaskCategory.JAVA_LEARNING;
        }
    }

    private TaskPriority parsePrioritySafe(String raw) {
        if (raw == null || raw.isBlank()) return TaskPriority.MEDIUM;
        try {
            return TaskPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TaskPriority.MEDIUM;
        }
    }

    private LocalDate parseDateSafe(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private LocalDate localToday(Long userId) {
        String tz = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(z -> z != null && !z.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
        return PeriodBoundaryHelper.localToday(Instant.now(), tz);
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR", "Unable to store AI proposal.");
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String str(Object val, String fallback) {
        if (val == null) return fallback;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? fallback : s;
    }

    private String trimToNull(String val) {
        if (val == null) return null;
        String t = val.trim();
        return t.isEmpty() ? null : t;
    }
}
