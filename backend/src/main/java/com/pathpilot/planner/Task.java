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
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "priority", nullable = false)
    private int priority = 2;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "carried_from_task_id")
    private Long carriedFromTaskId;

    @Column(name = "rescheduled_to_date")
    private LocalDate rescheduledToDate;

    @Column(name = "rescheduled_to_task_id")
    private Long rescheduledToTaskId;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled = false;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "reminder_at_utc")
    private Instant reminderAtUtc;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = "MANUAL";

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @Column(name = "deleted_at_utc")
    private Instant deletedAtUtc;

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
    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getTaskDate() { return taskDate; }
    public void setTaskDate(LocalDate taskDate) { this.taskDate = taskDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getCarriedFromTaskId() { return carriedFromTaskId; }
    public void setCarriedFromTaskId(Long carriedFromTaskId) { this.carriedFromTaskId = carriedFromTaskId; }
    public LocalDate getRescheduledToDate() { return rescheduledToDate; }
    public void setRescheduledToDate(LocalDate rescheduledToDate) { this.rescheduledToDate = rescheduledToDate; }
    public Long getRescheduledToTaskId() { return rescheduledToTaskId; }
    public void setRescheduledToTaskId(Long rescheduledToTaskId) { this.rescheduledToTaskId = rescheduledToTaskId; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
    public LocalTime getReminderTime() { return reminderTime; }
    public void setReminderTime(LocalTime reminderTime) { this.reminderTime = reminderTime; }
    public Instant getReminderAtUtc() { return reminderAtUtc; }
    public void setReminderAtUtc(Instant reminderAtUtc) { this.reminderAtUtc = reminderAtUtc; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public Instant getDeletedAtUtc() { return deletedAtUtc; }
    public void setDeletedAtUtc(Instant deletedAtUtc) { this.deletedAtUtc = deletedAtUtc; }
}
