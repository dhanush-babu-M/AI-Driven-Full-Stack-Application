package com.pathpilot.ai;

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
@Table(name = "ai_proposals")
public class AiProposal {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_entity", nullable = false, length = 60)
    private String targetEntity = "TASK";

    @Column(name = "proposal_type", nullable = false, length = 40)
    private String proposalType = "CREATE_TASK";

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "proposed_payload", nullable = false, columnDefinition = "JSON")
    private String proposedPayload;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = "AI";

    @Column(name = "proposal_date", nullable = false)
    private LocalDate proposalDate;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "reviewed_at_utc")
    private Instant reviewedAtUtc;

    @PrePersist
    void onCreate() {
        if (createdAtUtc == null) {
            createdAtUtc = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }
    public String getProposalType() { return proposalType; }
    public void setProposalType(String proposalType) { this.proposalType = proposalType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProposedPayload() { return proposedPayload; }
    public void setProposedPayload(String proposedPayload) { this.proposedPayload = proposedPayload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public LocalDate getProposalDate() { return proposalDate; }
    public void setProposalDate(LocalDate proposalDate) { this.proposalDate = proposalDate; }
    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
    public Instant getReviewedAtUtc() { return reviewedAtUtc; }
    public void setReviewedAtUtc(Instant reviewedAtUtc) { this.reviewedAtUtc = reviewedAtUtc; }
}
