package com.pathpilot.planner.dto;

import java.time.LocalTime;

public record TaskReminderRequest(
        boolean enabled,
        LocalTime reminderTime
) {
}
