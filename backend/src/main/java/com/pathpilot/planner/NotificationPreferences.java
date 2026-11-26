package com.pathpilot.planner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "daily_reminder_enabled", nullable = false)
    private boolean dailyReminderEnabled = true;

    @Column(name = "daily_reminder_time")
    private LocalTime dailyReminderTime;

    @Column(name = "weekly_digest_enabled", nullable = false)
    private boolean weeklyDigestEnabled = true;

    @Column(name = "monthly_report_ready_enabled", nullable = false)
    private boolean monthlyReportReadyEnabled = true;

    @Column(name = "browser_enabled", nullable = false)
    private boolean browserEnabled = false;

    @Column(name = "lead_minutes", nullable = false)
    private int leadMinutes = 10;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAtUtc == null) createdAtUtc = now;
        updatedAtUtc = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAtUtc = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isDailyReminderEnabled() { return dailyReminderEnabled; }
    public void setDailyReminderEnabled(boolean dailyReminderEnabled) { this.dailyReminderEnabled = dailyReminderEnabled; }
    public LocalTime getDailyReminderTime() { return dailyReminderTime; }
    public void setDailyReminderTime(LocalTime dailyReminderTime) { this.dailyReminderTime = dailyReminderTime; }
    public boolean isWeeklyDigestEnabled() { return weeklyDigestEnabled; }
    public void setWeeklyDigestEnabled(boolean weeklyDigestEnabled) { this.weeklyDigestEnabled = weeklyDigestEnabled; }
    public boolean isMonthlyReportReadyEnabled() { return monthlyReportReadyEnabled; }
    public void setMonthlyReportReadyEnabled(boolean monthlyReportReadyEnabled) { this.monthlyReportReadyEnabled = monthlyReportReadyEnabled; }
    public boolean isBrowserEnabled() { return browserEnabled; }
    public void setBrowserEnabled(boolean browserEnabled) { this.browserEnabled = browserEnabled; }
    public int getLeadMinutes() { return leadMinutes; }
    public void setLeadMinutes(int leadMinutes) { this.leadMinutes = leadMinutes; }
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
}
