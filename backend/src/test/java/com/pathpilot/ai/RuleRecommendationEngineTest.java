package com.pathpilot.ai;

import com.pathpilot.ai.RuleRecommendationEngine.RuleResult;
import com.pathpilot.ai.dto.AiDtos.DailyFactsDto;
import com.pathpilot.ai.dto.AiDtos.GoalGapDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleRecommendationEngineTest {

    private final RuleRecommendationEngine engine = new RuleRecommendationEngine();
    private final LocalDate today = LocalDate.of(2026, 11, 27);

    @Test
    void evaluate_handlesNoCodingRecordedYetWithoutShaming() {
        DailyFactsDto facts = new DailyFactsDto(today, null, 0, null, 0, 0, 0, 0, List.of());

        RuleResult result = engine.evaluate(facts);

        assertTrue(result.text().contains("not recorded any coding practice yet"));
        assertEquals("/app/coding", result.actionPath());
        assertEquals("DSA_PRACTICE", result.proposedTask().category());
        assertEquals(today, result.proposedTask().taskDate());
    }

    @Test
    void evaluate_flagsThreeDayCodingGap() {
        DailyFactsDto facts = new DailyFactsDto(today, 3, 2, "Old Task", 1, 0, 1, 1, List.of());

        RuleResult result = engine.evaluate(facts);

        assertTrue(result.text().contains("3 days"));
        assertEquals("DSA_PRACTICE", result.proposedTask().category());
    }

    @Test
    void evaluate_prioritizesOverdueTasksAfterRecentCoding() {
        DailyFactsDto facts = new DailyFactsDto(today, 1, 2, "Finish REST docs", 2, 0, 2, 0, List.of());

        RuleResult result = engine.evaluate(facts);

        assertTrue(result.text().contains("2 unfinished task(s)"));
        assertTrue(result.text().contains("Finish REST docs"));
        assertEquals("REVISION", result.proposedTask().category());
    }

    @Test
    void evaluate_highlightsUpcomingFollowupsBeforeGoalGaps() {
        DailyFactsDto facts = new DailyFactsDto(
                today,
                0,
                0,
                null,
                2,
                1,
                1,
                2,
                List.of(new GoalGapDto("Solve 10 problems", "CODING_PROBLEMS", 10, 4, 6))
        );

        RuleResult result = engine.evaluate(facts);

        assertTrue(result.text().contains("2 job follow-up(s)"));
        assertEquals("JOB_APPLICATIONS", result.proposedTask().category());
    }

    @Test
    void evaluate_targetsWeeklyGoalGapWhenNoFollowupsOrOverdueTasks() {
        DailyFactsDto facts = new DailyFactsDto(
                today,
                1,
                0,
                null,
                2,
                1,
                1,
                0,
                List.of(new GoalGapDto("Solve 10 DSA problems", "CODING_PROBLEMS", 10, 6, 4))
        );

        RuleResult result = engine.evaluate(facts);

        assertTrue(result.text().contains("Solve 10 DSA problems"));
        assertTrue(result.text().contains("6 of 10"));
        assertEquals("DSA_PRACTICE", result.proposedTask().category());
    }

    @Test
    void evaluate_promptsPlanningWhenTodayHasNoPlannedTasks() {
        DailyFactsDto facts = new DailyFactsDto(today, 0, 0, null, 0, 0, 0, 0, List.of());

        RuleResult result = engine.evaluate(facts);

        assertTrue(result.text().contains("No tasks are planned for today yet"));
        assertNotNull(result.proposedTask());
    }

    @Test
    void evaluate_encouragesPendingThenCompletedTodayTasks() {
        DailyFactsDto pendingFacts = new DailyFactsDto(today, 0, 0, null, 3, 1, 2, 0, List.of());
        RuleResult pendingResult = engine.evaluate(pendingFacts);
        assertTrue(pendingResult.text().contains("2 pending task(s) today"));

        DailyFactsDto doneFacts = new DailyFactsDto(today, 0, 0, null, 3, 3, 0, 0, List.of());
        RuleResult doneResult = engine.evaluate(doneFacts);
        assertTrue(doneResult.text().contains("All 3 planned task(s) for today are handled"));
    }
}
