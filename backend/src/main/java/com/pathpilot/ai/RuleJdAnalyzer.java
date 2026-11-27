package com.pathpilot.ai;

import com.pathpilot.ai.dto.AiDtos.InterviewQuestionsDto;
import com.pathpilot.user.dto.ProfileDtos.SkillDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic keyword-based fallback for Job Description analysis when AI is unavailable or unconfigured.
 * Strictly respects the user's saved Settings skills and projects.
 */
@Component
public class RuleJdAnalyzer {

    private static final Map<String, String> KNOWN_TECH_KEYWORDS = new LinkedHashMap<>();

    static {
        KNOWN_TECH_KEYWORDS.put("java", "Java");
        KNOWN_TECH_KEYWORDS.put("spring boot", "Spring Boot");
        KNOWN_TECH_KEYWORDS.put("spring security", "Spring Security");
        KNOWN_TECH_KEYWORDS.put("spring", "Spring");
        KNOWN_TECH_KEYWORDS.put("hibernate", "Hibernate / JPA");
        KNOWN_TECH_KEYWORDS.put("jpa", "JPA");
        KNOWN_TECH_KEYWORDS.put("sql", "SQL");
        KNOWN_TECH_KEYWORDS.put("mysql", "MySQL");
        KNOWN_TECH_KEYWORDS.put("postgresql", "PostgreSQL");
        KNOWN_TECH_KEYWORDS.put("react", "React");
        KNOWN_TECH_KEYWORDS.put("javascript", "JavaScript");
        KNOWN_TECH_KEYWORDS.put("typescript", "TypeScript");
        KNOWN_TECH_KEYWORDS.put("tailwind", "Tailwind CSS");
        KNOWN_TECH_KEYWORDS.put("html", "HTML");
        KNOWN_TECH_KEYWORDS.put("css", "CSS");
        KNOWN_TECH_KEYWORDS.put("rest api", "REST APIs");
        KNOWN_TECH_KEYWORDS.put("docker", "Docker");
        KNOWN_TECH_KEYWORDS.put("git", "Git");
        KNOWN_TECH_KEYWORDS.put("junit", "JUnit");
        KNOWN_TECH_KEYWORDS.put("mockito", "Mockito");
        KNOWN_TECH_KEYWORDS.put("microservices", "Microservices");
        KNOWN_TECH_KEYWORDS.put("aws", "AWS");
    }

    public RuleJdResult analyze(
            String sanitizedJd,
            List<SkillDto> profileSkills,
            List<ProjectRef> userProjects
    ) {
        String lowerJd = sanitizedJd == null ? "" : sanitizedJd.toLowerCase(Locale.ROOT);

        List<String> requiredSkills = new ArrayList<>();
        for (Map.Entry<String, String> entry : KNOWN_TECH_KEYWORDS.entrySet()) {
            if (lowerJd.contains(entry.getKey()) && !requiredSkills.contains(entry.getValue())) {
                requiredSkills.add(entry.getValue());
            }
        }
        if (requiredSkills.isEmpty()) {
            requiredSkills.addAll(List.of("Core Programming", "Problem Solving", "SQL"));
        }

        List<String> matchedSkills = new ArrayList<>();
        List<String> weakAreas = new ArrayList<>();
        List<String> gaps = new ArrayList<>();

        Map<String, SkillDto> profileByLower = new LinkedHashMap<>();
        if (profileSkills != null) {
            for (SkillDto s : profileSkills) {
                if (s != null && s.name() != null && !s.name().isBlank()) {
                    profileByLower.put(s.name().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }

        for (String req : requiredSkills) {
            SkillDto matched = findMatchingProfileSkill(req, profileByLower);
            if (matched != null) {
                matchedSkills.add(matched.name());
                if ("Beginner".equalsIgnoreCase(matched.level())) {
                    weakAreas.add(matched.name());
                }
            } else {
                gaps.add(req);
            }
        }

        List<String> checklist = new ArrayList<>();
        for (String gap : gaps.stream().limit(3).toList()) {
            checklist.add("Review fundamentals and build a small practice example for " + gap);
        }
        for (String weak : weakAreas.stream().limit(2).toList()) {
            checklist.add("Strengthen " + weak + " (currently marked Beginner in Settings)");
        }
        if (checklist.isEmpty()) {
            checklist.add("Prepare 2 STAR behavioural stories from your recent projects");
            checklist.add("Practice explaining architecture and trade-offs of your matched skills");
        }

        List<String> recommendedProjects = new ArrayList<>();
        if (userProjects != null) {
            for (ProjectRef proj : userProjects) {
                if (proj.title() == null) continue;
                String stackLower = proj.techStack() == null ? "" : proj.techStack().toLowerCase(Locale.ROOT);
                boolean overlaps = requiredSkills.stream()
                        .anyMatch(req -> stackLower.contains(req.toLowerCase(Locale.ROOT))
                                || lowerJd.contains(proj.title().toLowerCase(Locale.ROOT)));
                if (overlaps || recommendedProjects.isEmpty()) {
                    recommendedProjects.add(proj.title());
                }
                if (recommendedProjects.size() >= 3) break;
            }
        }

        InterviewQuestionsDto questions = new InterviewQuestionsDto(
                List.of("Explain OOP principles (encapsulation, inheritance, polymorphism) with a Java example.",
                        "How does HashMap work internally in Java?"),
                List.of("Explain how Spring Boot auto-configuration and dependency injection work.",
                        "How do you validate request bodies and handle errors globally in Spring MVC?"),
                List.of("Explain INNER JOIN vs LEFT JOIN and when to add a database index.",
                        "How would you prevent SQL injection in a Java backend?"),
                List.of("Explain React state vs props and how useEffect dependencies work.",
                        "How do you handle loading and error states cleanly in React components?"),
                List.of("Tell me about a technical bug you diagnosed and fixed on a recent project.",
                        "Describe a time you had to learn a new tool or framework quickly.")
        );

        return new RuleJdResult(
                requiredSkills,
                List.of(),
                matchedSkills.stream().distinct().toList(),
                gaps,
                weakAreas.stream().distinct().toList(),
                checklist,
                recommendedProjects,
                questions
        );
    }

    private SkillDto findMatchingProfileSkill(String req, Map<String, SkillDto> profileByLower) {
        String reqLower = req.toLowerCase(Locale.ROOT);
        if (profileByLower.containsKey(reqLower)) {
            return profileByLower.get(reqLower);
        }
        for (Map.Entry<String, SkillDto> entry : profileByLower.entrySet()) {
            if (reqLower.contains(entry.getKey()) || entry.getKey().contains(reqLower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public record ProjectRef(String title, String techStack) {
    }

    public record RuleJdResult(
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
