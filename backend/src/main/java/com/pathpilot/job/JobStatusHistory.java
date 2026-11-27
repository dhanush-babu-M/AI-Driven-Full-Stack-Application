package com.pathpilot.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_status_history")
public class JobStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_application_id", nullable = false)
    private Long jobApplicationId;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 40)
    private String newStatus;

    @Column(name = "changed_date", nullable = false)
    private LocalDate changedDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_at_utc", nullable = false, updatable = false)
    private Instant changedAtUtc;

    @PrePersist
    void onCreate() {
        if (changedAtUtc == null) {
            changedAtUtc = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getJobApplicationId() { return jobApplicationId; }
    public void setJobApplicationId(Long jobApplicationId) { this.jobApplicationId = jobApplicationId; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public LocalDate getChangedDate() { return changedDate; }
    public void setChangedDate(LocalDate changedDate) { this.changedDate = changedDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getChangedAtUtc() { return changedAtUtc; }
    public void setChangedAtUtc(Instant changedAtUtc) { this.changedAtUtc = changedAtUtc; }
}
