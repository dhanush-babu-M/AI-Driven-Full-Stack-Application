package com.pathpilot.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "connected_accounts")
public class ConnectedAccount {

    public static final String PROVIDER_GITHUB = "GITHUB";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "username_or_handle", nullable = false, length = 150)
    private String usernameOrHandle;

    @Column(name = "profile_url", length = 500)
    private String profileUrl;

    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = true;

    @Column(name = "encrypted_token", columnDefinition = "TEXT")
    private String encryptedToken;

    @Column(name = "etag", length = 255)
    private String etag;

    @Column(name = "last_synced_at_utc")
    private Instant lastSyncedAtUtc;

    @Column(name = "last_sync_status", length = 30)
    private String lastSyncStatus;

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

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
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getUsernameOrHandle() { return usernameOrHandle; }
    public void setUsernameOrHandle(String usernameOrHandle) { this.usernameOrHandle = usernameOrHandle; }
    public String getProfileUrl() { return profileUrl; }
    public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }
    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }
    public String getEncryptedToken() { return encryptedToken; }
    public void setEncryptedToken(String encryptedToken) { this.encryptedToken = encryptedToken; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public Instant getLastSyncedAtUtc() { return lastSyncedAtUtc; }
    public void setLastSyncedAtUtc(Instant lastSyncedAtUtc) { this.lastSyncedAtUtc = lastSyncedAtUtc; }
    public String getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }
    public String getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }
    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
}
