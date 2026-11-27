package com.pathpilot.job;

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
@Table(name = "job_followups")
public class JobFollowup {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(9, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_application_id", nullable = false)
    private Long jobApplicationId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "completed_at_utc")
    private Instant completedAtUtc;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime = DEFAULT_REMINDER_TIME;

    @Column(name = "reminder_at_utc")
    private Instant reminderAtUtc;

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
    public Long getJobApplicationId() { return jobApplicationId; }
    public void setJobApplicationId(Long jobApplicationId) { this.jobApplicationId = jobApplicationId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCompletedAtUtc() { return completedAtUtc; }
    public void setCompletedAtUtc(Instant completedAtUtc) { this.completedAtUtc = completedAtUtc; }
    public LocalTime getReminderTime() { return reminderTime; }
    public void setReminderTime(LocalTime reminderTime) { this.reminderTime = reminderTime; }
    public Instant getReminderAtUtc() { return reminderAtUtc; }
    public void setReminderAtUtc(Instant reminderAtUtc) { this.reminderAtUtc = reminderAtUtc; }
    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(Instant updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
    public Instant getDeletedAtUtc() { return deletedAtUtc; }
    public void setDeletedAtUtc(Instant deletedAtUtc) { this.deletedAtUtc = deletedAtUtc; }
}
