package com.pathpilot.project;

import com.pathpilot.project.ProjectService.MediaFilePayload;
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
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectSummaryDto> listProjects() {
        return projectService.listProjects(CurrentUser.id());
    }

    @GetMapping("/{projectId}")
    public ProjectDetailDto getProject(@PathVariable Long projectId) {
        return projectService.getProjectDetail(CurrentUser.id(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetailDto createProject(@Valid @RequestBody ProjectRequest request) {
        return projectService.createProject(CurrentUser.id(), request);
    }

    @PutMapping("/{projectId}")
    public ProjectDetailDto updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequest request
    ) {
        return projectService.updateProject(CurrentUser.id(), projectId, request);
    }

    @PatchMapping("/{projectId}/status")
    public ProjectDetailDto updateStatus(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectStatusRequest request
    ) {
        return projectService.updateProjectStatus(CurrentUser.id(), projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(CurrentUser.id(), projectId);
    }

    // -------------------------------------------------------------------------
    // Milestones
    // -------------------------------------------------------------------------

    @PostMapping("/{projectId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMilestoneDto createMilestone(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectMilestoneRequest request
    ) {
        return projectService.createMilestone(CurrentUser.id(), projectId, request);
    }

    @PutMapping("/{projectId}/milestones/{milestoneId}")
    public ProjectMilestoneDto updateMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody ProjectMilestoneRequest request
    ) {
        return projectService.updateMilestone(CurrentUser.id(), projectId, milestoneId, request);
    }

    @PatchMapping("/{projectId}/milestones/{milestoneId}/complete")
    public ProjectMilestoneDto toggleMilestoneComplete(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @RequestBody(required = false) MilestoneCompleteRequest request
    ) {
        MilestoneCompleteRequest body = request != null ? request : new MilestoneCompleteRequest(true, null);
        return projectService.toggleMilestoneCompletion(CurrentUser.id(), projectId, milestoneId, body);
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId
    ) {
        projectService.deleteMilestone(CurrentUser.id(), projectId, milestoneId);
    }

    // -------------------------------------------------------------------------
    // Work Logs
    // -------------------------------------------------------------------------

    @PostMapping("/{projectId}/worklogs")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectWorklogDto createWorklog(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectWorklogRequest request
    ) {
        return projectService.createWorklog(CurrentUser.id(), projectId, request);
    }

    @PutMapping("/{projectId}/worklogs/{worklogId}")
    public ProjectWorklogDto updateWorklog(
            @PathVariable Long projectId,
            @PathVariable Long worklogId,
            @Valid @RequestBody ProjectWorklogRequest request
    ) {
        return projectService.updateWorklog(CurrentUser.id(), projectId, worklogId, request);
    }

    @DeleteMapping("/{projectId}/worklogs/{worklogId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorklog(
            @PathVariable Long projectId,
            @PathVariable Long worklogId
    ) {
        projectService.deleteWorklog(CurrentUser.id(), projectId, worklogId);
    }

    // -------------------------------------------------------------------------
    // Media
    // -------------------------------------------------------------------------

    @PostMapping(value = "/{projectId}/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMediaDto uploadScreenshot(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption
    ) {
        return projectService.uploadScreenshot(CurrentUser.id(), projectId, file, caption);
    }

    @PostMapping("/{projectId}/media/video")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMediaDto addVideoLink(
            @PathVariable Long projectId,
            @Valid @RequestBody VideoLinkRequest request
    ) {
        return projectService.addVideoLink(CurrentUser.id(), projectId, request);
    }

    @GetMapping("/{projectId}/media/{mediaId}/file")
    public ResponseEntity<byte[]> getScreenshotFile(
            @PathVariable Long projectId,
            @PathVariable Long mediaId
    ) {
        MediaFilePayload payload = projectService.loadScreenshotFile(CurrentUser.id(), projectId, mediaId);
        MediaType contentType = payload.contentType() != null
                ? MediaType.parseMediaType(payload.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(payload.bytes());
    }

    @DeleteMapping("/{projectId}/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(
            @PathVariable Long projectId,
            @PathVariable Long mediaId
    ) {
        projectService.deleteMedia(CurrentUser.id(), projectId, mediaId);
    }
}
