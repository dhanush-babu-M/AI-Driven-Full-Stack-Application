package com.pathpilot.project;

import com.pathpilot.common.ApiException;
import com.pathpilot.common.UrlValidationUtil;
import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.dto.RecordHistoryDto;
import com.pathpilot.progress.ProgressCalculator;
import com.pathpilot.project.ProjectMediaStorageService.StoredScreenshot;
import com.pathpilot.project.dto.ProjectDtos.MilestoneCompleteRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectDetailDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectMediaDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectMilestoneDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectMilestoneRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectStatusRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectSummaryDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectWorklogDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectWorklogRequest;
import com.pathpilot.project.dto.ProjectDtos.VideoLinkRequest;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectService {

    public static final String ENTITY_PROJECT = "PROJECT";
    public static final int MAX_SCREENSHOTS_PER_PROJECT = 6;
    private static final Set<String> ALLOWED_PROJECT_STATUSES = Set.of("PLANNED", "CURRENT", "COMPLETED", "PAUSED");

    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectWorklogRepository worklogRepository;
    private final ProjectMediaRepository mediaRepository;
    private final ProjectMediaStorageService storageService;
    private final RecordHistoryRepository recordHistoryRepository;
    private final UserProfileRepository userProfileRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMilestoneRepository milestoneRepository,
            ProjectWorklogRepository worklogRepository,
            ProjectMediaRepository mediaRepository,
            ProjectMediaStorageService storageService,
            RecordHistoryRepository recordHistoryRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.worklogRepository = worklogRepository;
        this.mediaRepository = mediaRepository;
        this.storageService = storageService;
        this.recordHistoryRepository = recordHistoryRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> listProjects(Long userId) {
        return projectRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(userId)
                .stream()
                .map(project -> toSummaryDto(userId, project))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailDto getProjectDetail(Long userId, Long projectId) {
        Project project = requireOwnedProject(userId, projectId);
        return buildDetailDto(userId, project);
    }

    @Transactional
    public ProjectDetailDto createProject(Long userId, ProjectRequest request) {
        String normalizedStatus = normalizeProjectStatus(request.status(), "PLANNED");
        String liveUrl = UrlValidationUtil.validateOptionalHttpUrl(request.liveUrl(), "liveUrl");
        String repoUrl = UrlValidationUtil.validateOptionalHttpUrl(request.repoUrl(), "repoUrl");
        validateDateRange(request.startDate(), request.targetDate());

        Project project = new Project();
        project.setUserId(userId);
        project.setTitle(request.title().trim());
        project.setIcon(normalizeIcon(request.icon()));
        project.setDescription(UrlValidationUtil.trimToNull(request.description()));
        project.setTechStack(joinTechStack(request.techStack()));
        project.setLiveUrl(liveUrl);
        project.setRepoUrl(repoUrl);
        project.setStatus(normalizedStatus);
        project.setStartDate(request.startDate() != null ? request.startDate() : localToday(userId));
        project.setTargetDate(request.targetDate());
        project.setDataSource("MANUAL");

        Project saved = projectRepository.save(project);
        writeHistory(userId, saved.getId(), "CREATE", localToday(userId),
                "Created project \"" + saved.getTitle() + "\" with status " + normalizedStatus);

        return buildDetailDto(userId, saved);
    }

    @Transactional
    public ProjectDetailDto updateProject(Long userId, Long projectId, ProjectRequest request) {
        Project project = requireOwnedProject(userId, projectId);
        String previousStatus = normalizeProjectStatusSafe(project.getStatus());
        String nextStatus = normalizeProjectStatus(request.status(), previousStatus);
        String liveUrl = UrlValidationUtil.validateOptionalHttpUrl(request.liveUrl(), "liveUrl");
        String repoUrl = UrlValidationUtil.validateOptionalHttpUrl(request.repoUrl(), "repoUrl");
        validateDateRange(request.startDate(), request.targetDate());

        project.setTitle(request.title().trim());
        project.setIcon(normalizeIcon(request.icon()));
        project.setDescription(UrlValidationUtil.trimToNull(request.description()));
        project.setTechStack(joinTechStack(request.techStack()));
        project.setLiveUrl(liveUrl);
        project.setRepoUrl(repoUrl);
        project.setStatus(nextStatus);
        project.setStartDate(request.startDate());
        project.setTargetDate(request.targetDate());
        if (!"DEMO".equalsIgnoreCase(project.getDataSource())) {
            project.setDataSource("MANUAL");
        }

        Project saved = projectRepository.save(project);
        if (!previousStatus.equals(nextStatus)) {
            writeHistory(userId, saved.getId(), "STATUS_CHANGE", localToday(userId),
                    "Status changed from " + previousStatus + " to " + nextStatus);
        } else {
            writeHistory(userId, saved.getId(), "UPDATE", localToday(userId),
                    "Updated project details for \"" + saved.getTitle() + "\"");
        }

        return buildDetailDto(userId, saved);
    }

    @Transactional
    public ProjectDetailDto updateProjectStatus(Long userId, Long projectId, ProjectStatusRequest request) {
        Project project = requireOwnedProject(userId, projectId);
        String previousStatus = normalizeProjectStatusSafe(project.getStatus());
        String nextStatus = normalizeProjectStatus(request.status(), previousStatus);

        project.setStatus(nextStatus);
        project.setUpdatedAtUtc(Instant.now());
        Project saved = projectRepository.save(project);

        String note = UrlValidationUtil.trimToNull(request.note());
        String summary = "Status changed from " + previousStatus + " to " + nextStatus
                + (note != null ? " — " + note : "");
        writeHistory(userId, saved.getId(), "STATUS_CHANGE", localToday(userId), summary);

        return buildDetailDto(userId, saved);
    }

    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        Project project = requireOwnedProject(userId, projectId);
        project.setDeletedAtUtc(Instant.now());
        projectRepository.save(project);
        writeHistory(userId, project.getId(), "DELETE", localToday(userId),
                "Deleted project \"" + project.getTitle() + "\"");
    }

    // -------------------------------------------------------------------------
    // Milestones
    // -------------------------------------------------------------------------

    @Transactional
    public ProjectMilestoneDto createMilestone(Long userId, Long projectId, ProjectMilestoneRequest request) {
        Project project = requireOwnedProject(userId, projectId);
        List<ProjectMilestone> existing = milestoneRepository
                .findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderBySortOrderAscIdAsc(userId, projectId);

        String status = normalizeMilestoneStatus(request.status(), "PLANNED");
        LocalDate completedDate = resolveCompletedDateOnSave(userId, status, request.completedDate());

        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setUserId(userId);
        milestone.setProjectId(projectId);
        milestone.setTitle(request.title().trim());
        milestone.setDueDate(request.dueDate());
        milestone.setCompletedDate(completedDate);
        milestone.setStatus(status);
        milestone.setSortOrder(request.sortOrder() != null ? request.sortOrder() : existing.size() + 1);
        milestone.setNotes(UrlValidationUtil.trimToNull(request.notes()));
        milestone.setDataSource("MANUAL");

        ProjectMilestone saved = milestoneRepository.save(milestone);
        touchProject(project);
        return toMilestoneDto(saved);
    }

    @Transactional
    public ProjectMilestoneDto updateMilestone(
            Long userId,
            Long projectId,
            Long milestoneId,
            ProjectMilestoneRequest request
    ) {
        Project project = requireOwnedProject(userId, projectId);
        ProjectMilestone milestone = requireOwnedMilestone(userId, projectId, milestoneId);

        String status = normalizeMilestoneStatus(request.status(), milestone.getStatus());
        LocalDate completedDate = resolveCompletedDateOnSave(userId, status, request.completedDate());

        milestone.setTitle(request.title().trim());
        milestone.setDueDate(request.dueDate());
        milestone.setCompletedDate(completedDate);
        milestone.setStatus(status);
        if (request.sortOrder() != null) {
            milestone.setSortOrder(request.sortOrder());
        }
        milestone.setNotes(UrlValidationUtil.trimToNull(request.notes()));
        if (!"DEMO".equalsIgnoreCase(milestone.getDataSource())) {
            milestone.setDataSource("MANUAL");
        }

        ProjectMilestone saved = milestoneRepository.save(milestone);
        touchProject(project);
        return toMilestoneDto(saved);
    }

    @Transactional
    public ProjectMilestoneDto toggleMilestoneCompletion(
            Long userId,
            Long projectId,
            Long milestoneId,
            MilestoneCompleteRequest request
    ) {
        Project project = requireOwnedProject(userId, projectId);
        ProjectMilestone milestone = requireOwnedMilestone(userId, projectId, milestoneId);

        if (request.completed()) {
            milestone.setStatus(ProgressCalculator.STATUS_COMPLETED);
            milestone.setCompletedDate(request.completedDate() != null ? request.completedDate() : localToday(userId));
        } else {
            milestone.setStatus(ProgressCalculator.STATUS_PLANNED);
            milestone.setCompletedDate(null);
        }

        ProjectMilestone saved = milestoneRepository.save(milestone);
        touchProject(project);
        return toMilestoneDto(saved);
    }

    @Transactional
    public void deleteMilestone(Long userId, Long projectId, Long milestoneId) {
        Project project = requireOwnedProject(userId, projectId);
        ProjectMilestone milestone = requireOwnedMilestone(userId, projectId, milestoneId);
        milestone.setDeletedAtUtc(Instant.now());
        milestoneRepository.save(milestone);
        touchProject(project);
    }

    // -------------------------------------------------------------------------
    // Work Logs
    // -------------------------------------------------------------------------

    @Transactional
    public ProjectWorklogDto createWorklog(Long userId, Long projectId, ProjectWorklogRequest request) {
        Project project = requireOwnedProject(userId, projectId);

        ProjectWorklog worklog = new ProjectWorklog();
        worklog.setUserId(userId);
        worklog.setProjectId(projectId);
        worklog.setWorkDate(request.workDate());
        worklog.setMinutesSpent(request.minutesSpent() != null ? request.minutesSpent() : 0);
        worklog.setSummary(request.summary().trim());
        worklog.setDataSource("MANUAL");

        ProjectWorklog saved = worklogRepository.save(worklog);
        touchProject(project);
        return toWorklogDto(saved, project.getTitle());
    }

    @Transactional
    public ProjectWorklogDto updateWorklog(
            Long userId,
            Long projectId,
            Long worklogId,
            ProjectWorklogRequest request
    ) {
        Project project = requireOwnedProject(userId, projectId);
        ProjectWorklog worklog = requireOwnedWorklog(userId, projectId, worklogId);

        worklog.setWorkDate(request.workDate());
        if (request.minutesSpent() != null) {
            worklog.setMinutesSpent(request.minutesSpent());
        }
        worklog.setSummary(request.summary().trim());
        if (!"DEMO".equalsIgnoreCase(worklog.getDataSource())) {
            worklog.setDataSource("MANUAL");
        }

        ProjectWorklog saved = worklogRepository.save(worklog);
        touchProject(project);
        return toWorklogDto(saved, project.getTitle());
    }

    @Transactional
    public void deleteWorklog(Long userId, Long projectId, Long worklogId) {
        Project project = requireOwnedProject(userId, projectId);
        ProjectWorklog worklog = requireOwnedWorklog(userId, projectId, worklogId);
        worklog.setDeletedAtUtc(Instant.now());
        worklogRepository.save(worklog);
        touchProject(project);
    }

    // -------------------------------------------------------------------------
    // Media (Screenshots + Demo Video Links)
    // -------------------------------------------------------------------------

    @Transactional
    public ProjectMediaDto uploadScreenshot(Long userId, Long projectId, MultipartFile file, String caption) {
        Project project = requireOwnedProject(userId, projectId);
        long existingCount = mediaRepository.countByUserIdAndProjectIdAndMediaType(
                userId, projectId, ProjectMedia.TYPE_SCREENSHOT
        );
        if (existingCount >= MAX_SCREENSHOTS_PER_PROJECT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MAX_SCREENSHOTS_EXCEEDED",
                    "You can upload up to " + MAX_SCREENSHOTS_PER_PROJECT + " screenshots per project.",
                    Map.of("file", "Maximum of " + MAX_SCREENSHOTS_PER_PROJECT + " screenshots reached.")
            );
        }

        StoredScreenshot stored = storageService.storeScreenshot(file);
        ProjectMedia media = new ProjectMedia();
        media.setUserId(userId);
        media.setProjectId(projectId);
        media.setMediaType(ProjectMedia.TYPE_SCREENSHOT);
        media.setStoredFilename(stored.storedFilename());
        media.setOriginalFilename(truncateFilename(file.getOriginalFilename()));
        media.setContentType(stored.contentType());
        media.setFileSizeBytes(stored.fileSizeBytes());
        media.setCaption(UrlValidationUtil.trimToNull(caption));

        ProjectMedia saved = mediaRepository.save(media);
        touchProject(project);
        return toMediaDto(saved);
    }

    @Transactional
    public ProjectMediaDto addVideoLink(Long userId, Long projectId, VideoLinkRequest request) {
        Project project = requireOwnedProject(userId, projectId);
        String validUrl = UrlValidationUtil.validateOptionalHttpUrl(request.videoUrl(), "videoUrl");
        if (validUrl == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Demo video URL is required.",
                    Map.of("videoUrl", "Demo video URL is required.")
            );
        }

        ProjectMedia media = new ProjectMedia();
        media.setUserId(userId);
        media.setProjectId(projectId);
        media.setMediaType(ProjectMedia.TYPE_VIDEO_LINK);
        media.setVideoUrl(validUrl);
        media.setCaption(UrlValidationUtil.trimToNull(request.caption()));

        ProjectMedia saved = mediaRepository.save(media);
        touchProject(project);
        return toMediaDto(saved);
    }

    @Transactional(readOnly = true)
    public MediaFilePayload loadScreenshotFile(Long userId, Long projectId, Long mediaId) {
        requireOwnedProject(userId, projectId);
        ProjectMedia media = mediaRepository.findByIdAndUserIdAndProjectId(mediaId, userId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media item not found."));
        if (!ProjectMedia.TYPE_SCREENSHOT.equals(media.getMediaType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_FILE", "Requested media item is a video link.");
        }
        byte[] bytes = storageService.loadScreenshot(media.getStoredFilename());
        return new MediaFilePayload(bytes, media.getContentType(), media.getOriginalFilename());
    }

    @Transactional
    public void deleteMedia(Long userId, Long projectId, Long mediaId) {
        Project project = requireOwnedProject(userId, projectId);
        ProjectMedia media = mediaRepository.findByIdAndUserIdAndProjectId(mediaId, userId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media item not found."));

        if (ProjectMedia.TYPE_SCREENSHOT.equals(media.getMediaType())) {
            storageService.deleteScreenshot(media.getStoredFilename());
        }
        mediaRepository.delete(media);
        touchProject(project);
    }

    // -------------------------------------------------------------------------
    // Helpers & Mappers
    // -------------------------------------------------------------------------

    private ProjectDetailDto buildDetailDto(Long userId, Project project) {
        List<ProjectMilestone> milestones = milestoneRepository
                .findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderBySortOrderAscIdAsc(userId, project.getId());
        List<ProjectWorklog> worklogs = worklogRepository
                .findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderByWorkDateDescIdDesc(userId, project.getId());
        List<ProjectMedia> mediaList = mediaRepository
                .findAllByUserIdAndProjectIdOrderByCreatedAtUtcDescIdDesc(userId, project.getId());
        List<RecordHistory> history = recordHistoryRepository
                .findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(
                        userId, ENTITY_PROJECT, project.getId()
                );

        return new ProjectDetailDto(
                toSummaryDto(project, milestones, worklogs.size(), mediaList.size()),
                milestones.stream().map(this::toMilestoneDto).toList(),
                worklogs.stream().map(w -> toWorklogDto(w, project.getTitle())).toList(),
                mediaList.stream().map(this::toMediaDto).toList(),
                history.stream().map(h -> new RecordHistoryDto(
                        h.getId(),
                        h.getEntityType(),
                        h.getEntityId(),
                        h.getActionType(),
                        h.getActorSource(),
                        h.getRecordDate(),
                        h.getChangeSummary(),
                        h.getCreatedAtUtc()
                )).toList()
        );
    }

    private ProjectSummaryDto toSummaryDto(Long userId, Project project) {
        List<ProjectMilestone> milestones = milestoneRepository
                .findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderBySortOrderAscIdAsc(userId, project.getId());
        List<ProjectWorklog> worklogs = worklogRepository
                .findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderByWorkDateDescIdDesc(userId, project.getId());
        List<ProjectMedia> media = mediaRepository
                .findAllByUserIdAndProjectIdOrderByCreatedAtUtcDescIdDesc(userId, project.getId());
        return toSummaryDto(project, milestones, worklogs.size(), media.size());
    }

    private ProjectSummaryDto toSummaryDto(
            Project project,
            List<ProjectMilestone> milestones,
            int worklogCount,
            int mediaCount
    ) {
        List<String> statuses = milestones.stream().map(ProjectMilestone::getStatus).toList();
        int completed = (int) statuses.stream()
                .filter(ProgressCalculator.STATUS_COMPLETED::equalsIgnoreCase)
                .count();
        Double progress = ProgressCalculator.calculateProjectProgress(statuses);

        return new ProjectSummaryDto(
                project.getId(),
                project.getTitle(),
                project.getIcon(),
                project.getDescription(),
                splitTechStack(project.getTechStack()),
                project.getLiveUrl(),
                project.getRepoUrl(),
                normalizeProjectStatusSafe(project.getStatus()),
                project.getStartDate(),
                project.getTargetDate(),
                completed,
                milestones.size(),
                progress,
                worklogCount,
                mediaCount,
                project.getDataSource(),
                project.getCreatedAtUtc(),
                project.getUpdatedAtUtc()
        );
    }

    private ProjectMilestoneDto toMilestoneDto(ProjectMilestone m) {
        return new ProjectMilestoneDto(
                m.getId(),
                m.getProjectId(),
                m.getTitle(),
                m.getDueDate(),
                m.getCompletedDate(),
                normalizeMilestoneStatusSafe(m.getStatus()),
                m.getSortOrder(),
                m.getNotes(),
                m.getDataSource(),
                m.getCreatedAtUtc(),
                m.getUpdatedAtUtc()
        );
    }

    private ProjectWorklogDto toWorklogDto(ProjectWorklog w, String projectTitle) {
        return new ProjectWorklogDto(
                w.getId(),
                w.getProjectId(),
                projectTitle,
                w.getWorkDate(),
                w.getMinutesSpent(),
                w.getSummary(),
                w.getDataSource(),
                w.getCreatedAtUtc(),
                w.getUpdatedAtUtc()
        );
    }

    private ProjectMediaDto toMediaDto(ProjectMedia m) {
        String fileUrl = ProjectMedia.TYPE_SCREENSHOT.equals(m.getMediaType())
                ? "/api/projects/" + m.getProjectId() + "/media/" + m.getId() + "/file"
                : null;
        return new ProjectMediaDto(
                m.getId(),
                m.getProjectId(),
                m.getMediaType(),
                fileUrl,
                m.getOriginalFilename(),
                m.getContentType(),
                m.getFileSizeBytes(),
                m.getVideoUrl(),
                m.getCaption(),
                m.getCreatedAtUtc()
        );
    }

    private Project requireOwnedProject(Long userId, Long projectId) {
        return projectRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found."));
    }

    private ProjectMilestone requireOwnedMilestone(Long userId, Long projectId, Long milestoneId) {
        return milestoneRepository.findByIdAndUserIdAndProjectIdAndDeletedAtUtcIsNull(milestoneId, userId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found."));
    }

    private ProjectWorklog requireOwnedWorklog(Long userId, Long projectId, Long worklogId) {
        return worklogRepository.findByIdAndUserIdAndProjectIdAndDeletedAtUtcIsNull(worklogId, userId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORKLOG_NOT_FOUND", "Work log entry not found."));
    }

    private void touchProject(Project project) {
        project.setUpdatedAtUtc(Instant.now());
        projectRepository.save(project);
    }

    private String normalizeProjectStatus(String raw, String fallback) {
        String value = UrlValidationUtil.trimToNull(raw);
        if (value == null) {
            return fallback;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("IN_PROGRESS".equals(upper) || "ACTIVE".equals(upper)) {
            return "CURRENT";
        }
        if ("PENDING".equals(upper)) {
            return "PLANNED";
        }
        if (!ALLOWED_PROJECT_STATUSES.contains(upper)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Invalid project status.",
                    Map.of("status", "Status must be Planned, Current, Completed, or Paused.")
            );
        }
        return upper;
    }

    public static String normalizeProjectStatusSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PLANNED";
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if ("IN_PROGRESS".equals(upper) || "ACTIVE".equals(upper)) return "CURRENT";
        if ("PENDING".equals(upper)) return "PLANNED";
        return ALLOWED_PROJECT_STATUSES.contains(upper) ? upper : "PLANNED";
    }

    private String normalizeMilestoneStatus(String raw, String fallback) {
        String value = UrlValidationUtil.trimToNull(raw);
        if (value == null) {
            return fallback;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(upper)) {
            return ProgressCalculator.STATUS_COMPLETED;
        }
        if ("PLANNED".equals(upper) || "PENDING".equals(upper)) {
            return ProgressCalculator.STATUS_PLANNED;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid milestone status.",
                Map.of("status", "Milestone status must be Planned or Completed.")
        );
    }

    private String normalizeMilestoneStatusSafe(String raw) {
        if (raw != null && "COMPLETED".equalsIgnoreCase(raw.trim())) {
            return ProgressCalculator.STATUS_COMPLETED;
        }
        return ProgressCalculator.STATUS_PLANNED;
    }

    private LocalDate resolveCompletedDateOnSave(Long userId, String status, LocalDate requested) {
        if (!ProgressCalculator.STATUS_COMPLETED.equals(status)) {
            return null;
        }
        return requested != null ? requested : localToday(userId);
    }

    private void validateDateRange(LocalDate startDate, LocalDate targetDate) {
        if (startDate != null && targetDate != null && targetDate.isBefore(startDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Target date cannot be before start date.",
                    Map.of("targetDate", "Target date cannot be before start date.")
            );
        }
    }

    private String normalizeIcon(String raw) {
        String trimmed = UrlValidationUtil.trimToNull(raw);
        return trimmed != null ? trimmed : "code";
    }

    private String joinTechStack(List<String> techStack) {
        if (techStack == null || techStack.isEmpty()) {
            return null;
        }
        List<String> cleaned = techStack.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? null : String.join(", ", cleaned);
    }

    public static List<String> splitTechStack(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String truncateFilename(String raw) {
        String trimmed = UrlValidationUtil.trimToNull(raw);
        if (trimmed == null) return "screenshot";
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private LocalDate localToday(Long userId) {
        String tz = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(z -> z != null && !z.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
        return PeriodBoundaryHelper.localToday(Instant.now(), tz);
    }

    private void writeHistory(Long userId, Long projectId, String actionType, LocalDate date, String summary) {
        RecordHistory h = new RecordHistory();
        h.setUserId(userId);
        h.setEntityType(ENTITY_PROJECT);
        h.setEntityId(projectId);
        h.setActionType(actionType);
        h.setActorSource("MANUAL");
        h.setRecordDate(date);
        h.setChangeSummary(summary);
        recordHistoryRepository.save(h);
    }

    public record MediaFilePayload(byte[] bytes, String contentType, String originalFilename) {
    }
}
