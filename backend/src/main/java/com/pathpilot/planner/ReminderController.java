package com.pathpilot.planner;

import com.pathpilot.planner.dto.ReminderDtos.NotificationPreferencesDto;
import com.pathpilot.planner.dto.ReminderDtos.NotificationPreferencesRequest;
import com.pathpilot.planner.dto.ReminderDtos.UpcomingReminderDto;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/api/notifications/preferences")
    public ResponseEntity<NotificationPreferencesDto> getPreferences() {
        return ResponseEntity.ok(reminderService.getPreferences(CurrentUser.id()));
    }

    @PutMapping("/api/notifications/preferences")
    public ResponseEntity<NotificationPreferencesDto> updatePreferences(
            @Valid @RequestBody NotificationPreferencesRequest request
    ) {
        return ResponseEntity.ok(reminderService.updatePreferences(CurrentUser.id(), request));
    }

    @GetMapping("/api/reminders/upcoming")
    public ResponseEntity<List<UpcomingReminderDto>> getUpcomingReminders(
            @RequestParam(defaultValue = "24") int hours
    ) {
        return ResponseEntity.ok(reminderService.getUpcomingReminders(CurrentUser.id(), hours));
    }
}
