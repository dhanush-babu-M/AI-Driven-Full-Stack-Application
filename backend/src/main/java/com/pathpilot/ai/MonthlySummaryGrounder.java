package com.pathpilot.ai;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that every numeric token appearing in an AI-generated monthly summary
 * exists in the saved monthly snapshot numbers, preventing invented achievements or hours.
 */
@Component
public class MonthlySummaryGrounder {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");

    public String validate(MonthlySummaryPayload payload, Set<String> allowedNumbers) {
        if (payload == null) {
            return "Monthly summary payload cannot be null.";
        }
        if (isEmpty(payload.summaryParagraphs()) && isEmpty(payload.wentWell())) {
            return "Monthly summary must include summaryParagraphs or wentWell entries.";
        }

        List<String> allLines = new java.util.ArrayList<>();
        addAll(allLines, payload.summaryParagraphs());
        addAll(allLines, payload.wentWell());
        addAll(allLines, payload.unfinished());
        addAll(allLines, payload.nextMonthPriorities());

        for (String line : allLines) {
            Matcher matcher = NUMBER_PATTERN.matcher(line);
            while (matcher.find()) {
                String token = normalizeNumberToken(matcher.group());
                if (!allowedNumbers.contains(token)) {
                    return "Output cited number \"" + matcher.group()
                            + "\" which does not exist in the saved monthly snapshot.";
                }
            }
        }
        return null;
    }

    public Set<String> collectSnapshotNumbers(
            int year,
            int monthValue,
            int plannedTasks,
            int completedTasks,
            int pendingTasks,
            int skippedTasks,
            Double taskCompletionRate,
            int codingDays,
            int codingEntries,
            int codingMinutes,
            Double codingActivityRate,
            int learningDays,
            int learningMinutes,
            int worklogCount,
            int worklogMinutes,
            int milestonesCompleted,
            int milestonesTotal,
            int goalsDone,
            int goalsTotal,
            int applicationsCount
    ) {
        Set<String> allowed = new HashSet<>();
        addInt(allowed, year);
        addInt(allowed, monthValue);
        addInt(allowed, plannedTasks);
        addInt(allowed, completedTasks);
        addInt(allowed, pendingTasks);
        addInt(allowed, skippedTasks);
        addDouble(allowed, taskCompletionRate);
        addInt(allowed, codingDays);
        addInt(allowed, codingEntries);
        addInt(allowed, codingMinutes);
        addInt(allowed, codingMinutes / 60);
        addDouble(allowed, roundOne(codingMinutes / 60.0));
        addDouble(allowed, codingActivityRate);
        addInt(allowed, learningDays);
        addInt(allowed, learningMinutes);
        addInt(allowed, learningMinutes / 60);
        addDouble(allowed, roundOne(learningMinutes / 60.0));
        addInt(allowed, worklogCount);
        addInt(allowed, worklogMinutes);
        addInt(allowed, worklogMinutes / 60);
        addDouble(allowed, roundOne(worklogMinutes / 60.0));
        addInt(allowed, milestonesCompleted);
        addInt(allowed, milestonesTotal);
        addInt(allowed, goalsDone);
        addInt(allowed, goalsTotal);
        addInt(allowed, applicationsCount);
        return allowed;
    }

    private void addInt(Set<String> set, int val) {
        set.add(String.valueOf(val));
        if (val >= 0 && val < 10) {
            set.add("0" + val);
        }
    }

    private void addDouble(Set<String> set, Double val) {
        if (val == null) return;
        set.add(normalizeNumberToken(String.valueOf(val)));
        set.add(String.valueOf(Math.round(val)));
    }

    private String normalizeNumberToken(String token) {
        if (token.contains(".")) {
            // Normalize 50.0 -> 50 as well? Keep exact decimal and stripped .0
            if (token.endsWith(".0")) {
                return token.substring(0, token.length() - 2);
            }
            return token;
        }
        return token;
    }

    private double roundOne(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }

    private void addAll(List<String> target, List<String> src) {
        if (src != null) {
            for (String s : src) {
                if (s != null && !s.isBlank()) target.add(s);
            }
        }
    }

    public record MonthlySummaryPayload(
            List<String> summaryParagraphs,
            List<String> wentWell,
            List<String> unfinished,
            List<String> nextMonthPriorities
    ) {
    }
}
