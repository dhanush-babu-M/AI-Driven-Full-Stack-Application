package com.pathpilot.ai;

import com.pathpilot.ai.AiStructuredExecutor.ExecutionOutcome;
import com.pathpilot.ai.LlmClient.JsonSchema;
import com.pathpilot.ai.LlmClient.LlmPrompt;
import com.pathpilot.ai.RuleJdAnalyzer.ProjectRef;
import com.pathpilot.ai.RuleJdAnalyzer.RuleJdResult;
import com.pathpilot.ai.dto.AiDtos;
import com.pathpilot.ai.dto.AiDtos.InterviewQuestionsDto;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisRequest;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisResponse;
import com.pathpilot.common.ApiException;
import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.job.JobService;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import com.pathpilot.user.dto.ProfileDtos.SkillDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JdAnalysisService {

    private static final int MAX_JD_CHARS = 8000;

    private final JobApplicationRepository jobApplicationRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProjectRepository projectRepository;
    private final RuleJdAnalyzer ruleJdAnalyzer;
    private final AiStructuredExecutor structuredExecutor;
    private final ObjectMapper objectMapper;

    public JdAnalysisService(
            JobApplicationRepository jobApplicationRepository,
            UserProfileRepository userProfileRepository,
            ProjectRepository projectRepository,
            RuleJdAnalyzer ruleJdAnalyzer,
            AiStructuredExecutor structuredExecutor,
            ObjectMapper objectMapper
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userProfileRepository = userProfileRepository;
        this.projectRepository = projectRepository;
        this.ruleJdAnalyzer = ruleJdAnalyzer;
        this.structuredExecutor = structuredExecutor;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JdAnalysisResponse analyze(Long userId, JdAnalysisRequest request) {
        JobApplication jobApp = null;
        String rawJd = request.jobDescription();

        if (request.jobApplicationId() != null) {
            jobApp = jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(request.jobApplicationId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job application not found."));
            if (rawJd == null || rawJd.isBlank()) {
                rawJd = jobApp.getJobDescription();
            }
        }

        String sanitizedJd = JobService.sanitizePlainText(rawJd);
        if (sanitizedJd == null || sanitizedJd.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Paste a job description first before running analysis.",
                    Map.of("jobDescription", "Job description text is required.")
            );
        }
        if (sanitizedJd.length() > MAX_JD_CHARS) {
            sanitizedJd = sanitizedJd.substring(0, MAX_JD_CHARS);
        }

        List<SkillDto> profileSkills = readProfileSkills(userId);
        List<ProjectRef> userProjects = projectRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(userId)
                .stream()
                .map(p -> new ProjectRef(p.getTitle(), p.getTechStack()))
                .toList();

        RuleJdResult ruleResult = ruleJdAnalyzer.analyze(sanitizedJd, profileSkills, userProjects);

        Set<String> allowedProfileSkillNames = profileSkills.stream()
                .map(s -> s.name().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> beginnerSkillNames = profileSkills.stream()
                .filter(s -> "Beginner".equalsIgnoreCase(s.level()))
                .map(s -> s.name().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> allowedProjectTitles = userProjects.stream()
                .map(p -> p.title().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        LlmPrompt prompt = buildPrompt(sanitizedJd, profileSkills, userProjects);
        JsonSchema schema = buildSchema();

        ExecutionOutcome<LlmJdPayload> outcome = structuredExecutor.execute(
                prompt,
                schema,
                LlmJdPayload.class,
                payload -> validatePayload(payload, allowedProfileSkillNames, beginnerSkillNames, allowedProjectTitles)
        );

        Instant now = Instant.now();
        JdAnalysisResponse response;
        if (outcome.success() && outcome.data() != null) {
            LlmJdPayload ai = outcome.data();
            response = new JdAnalysisResponse(
                    jobApp != null ? jobApp.getId() : null,
                    "AI",
                    AiDtos.AI_DISCLAIMER + " Suggestions only — never exaggerates your recorded skill levels.",
                    null,
                    safeList(ai.requiredSkills()),
                    safeList(ai.niceToHave()),
                    safeList(ai.matchedSkills()),
                    safeList(ai.gaps()),
                    safeList(ai.weakAreas()),
                    safeList(ai.preparationChecklist()),
                    safeList(ai.recommendedProjectsToShow()),
                    ai.interviewQuestions() != null ? ai.interviewQuestions() : ruleResult.interviewQuestions(),
                    now
            );
        } else {
            response = new JdAnalysisResponse(
                    jobApp != null ? jobApp.getId() : null,
                    "RULE",
                    "Rule-based suggestion from your saved Settings skills and projects.",
                    outcome.notice(),
                    ruleResult.requiredSkills(),
                    ruleResult.niceToHave(),
                    ruleResult.matchedSkills(),
                    ruleResult.gaps(),
                    ruleResult.weakAreas(),
                    ruleResult.preparationChecklist(),
                    ruleResult.recommendedProjectsToShow(),
                    ruleResult.interviewQuestions(),
                    now
            );
        }

        if (jobApp != null) {
            jobApp.setAiJdAnalysisJson(writeJson(response));
            jobApp.setAiJdAnalyzedAtUtc(now);
            jobApplicationRepository.save(jobApp);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public JdAnalysisResponse getSavedAnalysis(Long userId, Long jobApplicationId) {
        JobApplication jobApp = jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(jobApplicationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job application not found."));
        if (jobApp.getAiJdAnalysisJson() == null || jobApp.getAiJdAnalysisJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jobApp.getAiJdAnalysisJson(), JdAnalysisResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String validatePayload(
            LlmJdPayload payload,
            Set<String> allowedProfileSkillNames,
            Set<String> beginnerSkillNames,
            Set<String> allowedProjectTitles
    ) {
        if (payload == null) {
            return "Analysis response cannot be null.";
        }
        for (String matched : safeList(payload.matchedSkills())) {
            if (!allowedProfileSkillNames.contains(matched.trim().toLowerCase(Locale.ROOT))) {
                return "matchedSkills may ONLY contain exact skill names from the user's saved profile: invalid \"" + matched + "\".";
            }
        }
        for (String weak : safeList(payload.weakAreas())) {
            if (!beginnerSkillNames.contains(weak.trim().toLowerCase(Locale.ROOT))) {
                return "weakAreas may ONLY contain skills marked Beginner in the user's saved profile: invalid \"" + weak + "\".";
            }
        }
        for (String proj : safeList(payload.recommendedProjectsToShow())) {
            if (!allowedProjectTitles.contains(proj.trim().toLowerCase(Locale.ROOT))) {
                return "recommendedProjectsToShow may ONLY contain exact titles from the user's saved projects: invalid \"" + proj + "\".";
            }
        }
        return null;
    }

    private LlmPrompt buildPrompt(String sanitizedJd, List<SkillDto> skills, List<ProjectRef> projects) {
        String system = """
                You are a career coach analyzing a job description against a user's saved profile.
                Strict honesty rules:
                - matchedSkills MUST ONLY contain exact skill names present in the supplied user profile skills list.
                - weakAreas MUST ONLY contain exact skill names marked with level "Beginner" in the user profile.
                - gaps MUST list required/important JD skills not present in the user profile.
                - recommendedProjectsToShow MUST ONLY contain exact titles from the supplied user projects list (empty if none match).
                - NEVER claim a skill level higher than the profile states.
                - Return valid JSON matching the schema.
                """;
        String userMsg = "User Profile Skills (with levels):\n" + writeJson(skills)
                + "\n\nUser Projects:\n" + writeJson(projects)
                + "\n\nJob Description (plain text):\n" + sanitizedJd;
        return new LlmPrompt(system, userMsg);
    }

    private JsonSchema buildSchema() {
        Map<String, Object> strArray = Map.of("type", "array", "items", Map.of("type", "string"));
        Map<String, Object> iqSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "java", strArray,
                        "springBoot", strArray,
                        "sql", strArray,
                        "react", strArray,
                        "behavioural", strArray
                ),
                "required", List.of("java", "springBoot", "sql", "react", "behavioural")
        );
        Map<String, Object> root = Map.of(
                "type", "object",
                "properties", Map.of(
                        "requiredSkills", strArray,
                        "niceToHave", strArray,
                        "matchedSkills", strArray,
                        "gaps", strArray,
                        "weakAreas", strArray,
                        "preparationChecklist", strArray,
                        "recommendedProjectsToShow", strArray,
                        "interviewQuestions", iqSchema
                ),
                "required", List.of(
                        "requiredSkills", "niceToHave", "matchedSkills", "gaps",
                        "weakAreas", "preparationChecklist", "recommendedProjectsToShow", "interviewQuestions"
                )
        );
        return new JsonSchema("jd_analysis", root);
    }

    private List<SkillDto> readProfileSkills(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null || profile.getSkillsJson() == null || profile.getSkillsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(profile.getSkillsJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
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

    public record LlmJdPayload(
            List<String> requiredSkills,
            List<String> niceToHave,
            List<String> matchedSkills,
            List<String> gaps,
            List<String> weakAreas,
            List<String> preparationChecklist,
            List<String> recommendedProjectsToShow,
            InterviewQuestionsDto interviewQuestions
    ) {
    }
}
