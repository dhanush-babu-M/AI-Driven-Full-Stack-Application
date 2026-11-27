package com.pathpilot.ai;

import com.pathpilot.ai.LlmClient.LlmException;
import com.pathpilot.ai.LlmClient.LlmResult;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisRequest;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisResponse;
import com.pathpilot.common.ApiException;
import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdAnalysisServiceTest {

    private JobApplicationRepository jobApplicationRepository;
    private UserProfileRepository userProfileRepository;
    private ProjectRepository projectRepository;
    private LlmClient llmClient;
    private JdAnalysisService jdAnalysisService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobApplicationRepository = mock(JobApplicationRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        projectRepository = mock(ProjectRepository.class);
        llmClient = mock(LlmClient.class);
        objectMapper = new ObjectMapper();

        AiStructuredExecutor executor = new AiStructuredExecutor(llmClient, objectMapper);
        jdAnalysisService = new JdAnalysisService(
                jobApplicationRepository,
                userProfileRepository,
                projectRepository,
                new RuleJdAnalyzer(),
                executor,
                objectMapper
        );

        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setSkillsJson("""
                [
                  {"name":"Java","level":"Comfortable"},
                  {"name":"Spring Boot","level":"Working"},
                  {"name":"Docker","level":"Beginner"}
                ]
                """);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        Project pathPilot = new Project();
        pathPilot.setId(10L);
        pathPilot.setUserId(1L);
        pathPilot.setTitle("PathPilot");
        pathPilot.setTechStack("Java, Spring Boot, React");
        when(projectRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(1L))
                .thenReturn(List.of(pathPilot));
    }

    @Test
    void analyze_retriesOnceWhenLlmHallucinatesMatchedSkillThenSucceedsWithValidJson() {
        JobApplication app = new JobApplication();
        app.setId(5L);
        app.setUserId(1L);
        app.setJobDescription("<p>Need Java, Spring Boot, Docker, and Kubernetes experience.</p>");
        when(jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(5L, 1L)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        String badJson = """
                {
                  "requiredSkills": ["Java", "Spring Boot", "Kubernetes"],
                  "niceToHave": ["Docker"],
                  "matchedSkills": ["Java", "Kubernetes"],
                  "gaps": [],
                  "weakAreas": ["Docker"],
                  "preparationChecklist": ["Review REST controllers"],
                  "recommendedProjectsToShow": ["PathPilot"],
                  "interviewQuestions": {
                    "java": ["Explain HashMap internals."],
                    "springBoot": ["How does @Transactional work?"],
                    "sql": ["Explain indexes."],
                    "react": ["Explain useEffect."],
                    "behavioural": ["Tell me about a bug you fixed."]
                  }
                }
                """;
        String goodJson = """
                {
                  "requiredSkills": ["Java", "Spring Boot", "Kubernetes"],
                  "niceToHave": ["Docker"],
                  "matchedSkills": ["Java", "Spring Boot"],
                  "gaps": ["Kubernetes"],
                  "weakAreas": ["Docker"],
                  "preparationChecklist": ["Review Spring Boot REST controllers", "Practice Docker basics"],
                  "recommendedProjectsToShow": ["PathPilot"],
                  "interviewQuestions": {
                    "java": ["Explain HashMap internals."],
                    "springBoot": ["How does @Transactional work?"],
                    "sql": ["Explain indexes."],
                    "react": ["Explain useEffect."],
                    "behavioural": ["Tell me about a bug you fixed."]
                  }
                }
                """;

        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.generate(any(), any()))
                .thenReturn(new LlmResult(badJson, "gemini-3.5-flash-lite", "gemini"))
                .thenReturn(new LlmResult(goodJson, "gemini-3.5-flash-lite", "gemini"));

        JdAnalysisResponse result = jdAnalysisService.analyze(1L, new JdAnalysisRequest(5L, null));

        verify(llmClient, times(2)).generate(any(), any());
        assertEquals("AI", result.source());
        assertTrue(result.disclaimer().contains("AI-generated. Based on your saved records."));
        assertEquals(List.of("Java", "Spring Boot"), result.matchedSkills());
        assertEquals(List.of("Docker"), result.weakAreas());
        assertEquals(List.of("PathPilot"), result.recommendedProjectsToShow());
        assertNotNull(app.getAiJdAnalysisJson());
    }

    @Test
    void analyze_fallsBackToRuleEngineWhenQuotaExhaustedAndRespectsProfileLevels() {
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.generate(any(), any()))
                .thenThrow(new LlmException(LlmException.Reason.QUOTA_EXHAUSTED, "Daily quota exceeded"));

        JdAnalysisResponse result = jdAnalysisService.analyze(
                1L,
                new JdAnalysisRequest(null, "<h1>Backend Engineer</h1> Looking for Java, Spring Boot, Docker, SQL, React")
        );

        assertEquals("RULE", result.source());
        assertNotNull(result.notice());
        assertTrue(result.matchedSkills().containsAll(List.of("Java", "Spring Boot", "Docker")));
        assertEquals(List.of("Docker"), result.weakAreas());
        assertTrue(result.gaps().contains("SQL"));
        assertEquals(List.of("PathPilot"), result.recommendedProjectsToShow());
    }

    @Test
    void analyze_rejectsNonOwnedJobApplication() {
        when(jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(99L, 1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> jdAnalysisService.analyze(1L, new JdAnalysisRequest(99L, "Java Spring Boot")));

        assertEquals(404, ex.getStatus().value());
    }
}
