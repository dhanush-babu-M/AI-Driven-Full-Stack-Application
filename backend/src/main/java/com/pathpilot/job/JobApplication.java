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

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "role_title", nullable = false, length = 200)
    private String roleTitle;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "APPLIED";

    @Column(name = "applied_date", nullable = false)
    private LocalDate appliedDate;

    @Column(name = "job_url", length = 500)
    private String jobUrl;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "source", nullable = false, length = 40)
    private String source = "OTHER";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recruiter_notes", columnDefinition = "TEXT")
    private String recruiterNotes;

    @Column(name = "followup_date")
    private LocalDate followupDate;

    @Column(name = "interview_date")
    private LocalDate interviewDate;

    @Column(name = "prep_checklist_json", columnDefinition = "TEXT")
    private String prepChecklistJson;

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
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getRoleTitle() { return roleTitle; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
    public String getJobUrl() { return jobUrl; }
    public void setJobUrl(String jobUrl) { this.jobUrl = jobUrl; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecruiterNotes() { return recruiterNotes; }
    public void setRecruiterNotes(String recruiterNotes) { this.recruiterNotes = recruiterNotes; }
    public LocalDate getFollowupDate() { return followupDate; }
    public void setFollowupDate(LocalDate followupDate) { this.followupDate = followupDate; }
    public LocalDate getInterviewDate() { return interviewDate; }
    public void setInterviewDate(LocalDate interviewDate) { this.interviewDate = interviewDate; }
    public String getPrepChecklistJson() { return prepChecklistJson; }
    public void setPrepChecklistJson(String prepChecklistJson) { this.prepChecklistJson = prepChecklistJson; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(Instant updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
    public Instant getDeletedAtUtc() { return deletedAtUtc; }
    public void setDeletedAtUtc(Instant deletedAtUtc) { this.deletedAtUtc = deletedAtUtc; }
}
