package com.pathpilot.ai;

import com.pathpilot.ai.dto.AiDtos.DailyFactsDto;
import com.pathpilot.ai.dto.AiDtos.GoalGapDto;
import com.pathpilot.ai.dto.AiDtos.ProposedTaskDraftDto;
import org.springframework.stereotype.Component;

/**
 * Pure Java deterministic rule engine that evaluates saved career-tracking facts
 * before any optional LLM wording is requested.
 */
@Component
public class RuleRecommendationEngine {

    public RuleResult evaluate(DailyFactsDto facts) {
        if (facts == null) {
            return new RuleResult(
                    "Plan 2–3 focused tasks for today to build steady momentum.",
                    "Plan today",
                    "/app/planner/daily",
                    new ProposedTaskDraftDto(
                            "Plan today's top 3 study & project tasks",
                            "Break today into clear, measurable blocks.",
                            "JAVA_LEARNING",
                            null,
                            "MEDIUM"
                    )
            );
        }

        // 1. Coding inactivity check (unrecorded is NOT failed; suggest concrete gentle action)
        if (facts.daysSinceLastCoding() == null) {
            return new RuleResult(
                    "You have not recorded any coding practice yet. Try solving one problem today and record what you learned.",
                    "Log coding",
                    "/app/coding",
                    new ProposedTaskDraftDto(
                            "Solve 1 coding problem & log notes",
                            "Log platform, topic, and takeaway in Coding Activity.",
                            "DSA_PRACTICE",
                            facts.date(),
                            "HIGH"
                    )
            );
        }

        if (facts.daysSinceLastCoding() >= 3) {
            return new RuleResult(
                    "You have not recorded coding practice for " + facts.daysSinceLastCoding()
                            + " days. Try one Java array problem today and record what you learned.",
                    "Log coding",
                    "/app/coding",
                    new ProposedTaskDraftDto(
                            "Solve 1 Java array problem & record what you learned",
                            "Focus on a single problem and capture key takeaways.",
                            "DSA_PRACTICE",
                            facts.date(),
                            "HIGH"
                    )
            );
        }

        // 2. Overdue tasks
        if (facts.overdueTasksCount() > 0) {
            String topTitle = facts.topOverdueTaskTitle() != null && !facts.topOverdueTaskTitle().isBlank()
                    ? " (including \"" + facts.topOverdueTaskTitle() + "\")"
                    : "";
            return new RuleResult(
                    "You have " + facts.overdueTasksCount() + " unfinished task(s) from earlier days"
                            + topTitle + ". Review and carry forward or complete the most important one today.",
                    "Review tasks",
                    "/app/planner/daily",
                    new ProposedTaskDraftDto(
                            "Review & finish top overdue task",
                            "Tackle or explicitly reschedule leftover work.",
                            "REVISION",
                            facts.date(),
                            "HIGH"
                    )
            );
        }

        // 3. Upcoming job follow-ups
        if (facts.upcomingFollowupsCount() > 0) {
            return new RuleResult(
                    "You have " + facts.upcomingFollowupsCount()
                            + " job follow-up(s) due soon. Send a concise follow-up message and update your application status.",
                    "Open applications",
                    "/app/projects?tab=applications",
                    new ProposedTaskDraftDto(
                            "Send pending job application follow-up",
                            "Review your follow-up list and update status notes.",
                            "JOB_APPLICATIONS",
                            facts.date(),
                            "HIGH"
                    )
            );
        }

        // 4. Weekly goal gaps
        if (facts.weeklyGoalGaps() != null && !facts.weeklyGoalGaps().isEmpty()) {
            GoalGapDto gap = facts.weeklyGoalGaps().get(0);
            String taskCategory = mapGoalCategoryToTaskCategory(gap.category());
            return new RuleResult(
                    "Your weekly goal \"" + gap.title() + "\" is at " + gap.doneCount() + " of "
                            + gap.targetCount() + " (" + gap.remainingCount()
                            + " remaining). Dedicate one session to close the gap.",
                    "Open weekly planner",
                    "/app/planner/weekly",
                    new ProposedTaskDraftDto(
                            "Progress weekly goal: " + gap.title(),
                            "Target remaining count: " + gap.remainingCount(),
                            taskCategory,
                            facts.date(),
                            "MEDIUM"
                    )
            );
        }

        // 5. No tasks planned today
        if (facts.plannedTodayCount() == 0) {
            return new RuleResult(
                    "No tasks are planned for today yet. Add 2–3 focused tasks in Daily Planner to build daily momentum.",
                    "Plan today",
                    "/app/planner/daily",
                    new ProposedTaskDraftDto(
                            "Plan today's 3 focused career tasks",
                            "Mix learning, coding practice, and project progress.",
                            "JAVA_LEARNING",
                            facts.date(),
                            "MEDIUM"
                    )
            );
        }

        // 6. Pending tasks today
        if (facts.pendingTodayCount() > 0) {
            return new RuleResult(
                    "You have " + facts.pendingTodayCount() + " pending task(s) today ("
                            + facts.completedTodayCount() + "/" + facts.plannedTodayCount()
                            + " completed). Focus on your top priority item next.",
                    "Open planner",
                    "/app/planner/daily",
                    new ProposedTaskDraftDto(
                            "Complete next pending task for today",
                            "Finish one pending item before adding new scope.",
                            "PROJECT_WORK",
                            facts.date(),
                            "MEDIUM"
                    )
            );
        }

        // 7. Everything planned today is complete
        return new RuleResult(
                "All " + facts.plannedTodayCount()
                        + " planned task(s) for today are handled! Log a project work entry or note what you learned.",
                "View projects",
                "/app/projects",
                new ProposedTaskDraftDto(
                        "Log project work or daily learning reflection",
                        "Capture concrete progress from today's work.",
                        "PROJECT_WORK",
                        facts.date(),
                        "LOW"
                )
        );
    }

    private String mapGoalCategoryToTaskCategory(String goalCategory) {
        if (goalCategory == null) return "JAVA_LEARNING";
        return switch (goalCategory.toUpperCase()) {
            case "CODING_PROBLEMS" -> "DSA_PRACTICE";
            case "PROJECTS" -> "PROJECT_WORK";
            case "JOB_APPLICATIONS" -> "JOB_APPLICATIONS";
            case "REVISION" -> "REVISION";
            default -> "JAVA_LEARNING";
        };
    }

    public record RuleResult(
            String text,
            String actionLabel,
            String actionPath,
            ProposedTaskDraftDto proposedTask
    ) {
    }
}
