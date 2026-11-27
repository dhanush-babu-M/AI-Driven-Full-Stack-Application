package com.pathpilot.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "monthly_reports")
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String reportMonth;

    @Column(name = "planned_tasks", nullable = false)
    private int plannedTasks;

    @Column(name = "completed_tasks", nullable = false)
    private int completedTasks;

    @Column(name = "coding_days", nullable = false)
    private int codingDays;

    @Column(name = "learning_days", nullable = false)
    private int learningDays;

    @Column(name = "task_completion_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taskCompletionRate = BigDecimal.ZERO;

    @Column(name = "coding_activity_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal codingActivityRate = BigDecimal.ZERO;

    @Column(name = "summary_notes", columnDefinition = "TEXT")
    private String summaryNotes;

    @Column(name = "ai_summary_json", columnDefinition = "TEXT")
    private String aiSummaryJson;

    @Column(name = "ai_summary_generated_at_utc")
    private Instant aiSummaryGeneratedAtUtc;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = "CALCULATED";

    @Column(name = "generated_at_utc", nullable = false)
    private Instant generatedAtUtc;

    @PrePersist
    void onCreate() {
        if (generatedAtUtc == null) {
            generatedAtUtc = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReportMonth() { return reportMonth; }
    public void setReportMonth(String reportMonth) { this.reportMonth = reportMonth; }
    public int getPlannedTasks() { return plannedTasks; }
    public void setPlannedTasks(int plannedTasks) { this.plannedTasks = plannedTasks; }
    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
    public int getCodingDays() { return codingDays; }
    public void setCodingDays(int codingDays) { this.codingDays = codingDays; }
    public int getLearningDays() { return learningDays; }
    public void setLearningDays(int learningDays) { this.learningDays = learningDays; }
    public BigDecimal getTaskCompletionRate() { return taskCompletionRate; }
    public void setTaskCompletionRate(BigDecimal taskCompletionRate) { this.taskCompletionRate = taskCompletionRate; }
    public BigDecimal getCodingActivityRate() { return codingActivityRate; }
    public void setCodingActivityRate(BigDecimal codingActivityRate) { this.codingActivityRate = codingActivityRate; }
    public String getSummaryNotes() { return summaryNotes; }
    public void setSummaryNotes(String summaryNotes) { this.summaryNotes = summaryNotes; }
    public String getAiSummaryJson() { return aiSummaryJson; }
    public void setAiSummaryJson(String aiSummaryJson) { this.aiSummaryJson = aiSummaryJson; }
    public Instant getAiSummaryGeneratedAtUtc() { return aiSummaryGeneratedAtUtc; }
    public void setAiSummaryGeneratedAtUtc(Instant aiSummaryGeneratedAtUtc) { this.aiSummaryGeneratedAtUtc = aiSummaryGeneratedAtUtc; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Instant getGeneratedAtUtc() { return generatedAtUtc; }
    public void setGeneratedAtUtc(Instant generatedAtUtc) { this.generatedAtUtc = generatedAtUtc; }
}
