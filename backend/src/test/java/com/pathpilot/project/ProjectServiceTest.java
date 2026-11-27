package com.pathpilot.project;

import com.pathpilot.common.ApiException;
import com.pathpilot.config.AppProperties;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.project.dto.ProjectDtos.MilestoneCompleteRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectDetailDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectMediaDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectMilestoneDto;
import com.pathpilot.project.dto.ProjectDtos.ProjectMilestoneRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectStatusRequest;
import com.pathpilot.project.dto.ProjectDtos.ProjectWorklogRequest;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    @TempDir
    Path tempDir;

    private ProjectRepository projectRepository;
    private ProjectMilestoneRepository milestoneRepository;
    private ProjectWorklogRepository worklogRepository;
    private ProjectMediaRepository mediaRepository;
    private RecordHistoryRepository recordHistoryRepository;
    private UserProfileRepository userProfileRepository;
    private ProjectService projectService;

    private final Map<Long, Project> projects = new ConcurrentHashMap<>();
    private final Map<Long, ProjectMilestone> milestones = new ConcurrentHashMap<>();
    private final Map<Long, ProjectWorklog> worklogs = new ConcurrentHashMap<>();
    private final Map<Long, ProjectMedia> mediaMap = new ConcurrentHashMap<>();
    private final List<RecordHistory> histories = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        milestoneRepository = mock(ProjectMilestoneRepository.class);
        worklogRepository = mock(ProjectWorklogRepository.class);
        mediaRepository = mock(ProjectMediaRepository.class);
        recordHistoryRepository = mock(RecordHistoryRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);

        AppProperties props = new AppProperties(
                "PathPilot",
                "Track . Learn . Grow",
                new AppProperties.CorsProperties(List.of("http://localhost:5173")),
                new AppProperties.JwtProperties("secret-at-least-32-chars-for-tests-only", 30, "pathpilot_token", false, "Lax"),
                new AppProperties.RateLimitProperties(5, 60),
                new AppProperties.GithubProperties("https://api.github.com", 5000, 10000, null),
                new AppProperties.UploadsProperties(tempDir.toString(), 2L * 1024 * 1024),
                null
        );
        ProjectMediaStorageService storageService = new ProjectMediaStorageService(props);

        projectService = new ProjectService(
                projectRepository,
                milestoneRepository,
                worklogRepository,
                mediaRepository,
                storageService,
                recordHistoryRepository,
                userProfileRepository
        );

        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setTimeZone("UTC");
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            if (p.getId() == null) p.setId(seq.getAndIncrement());
            projects.put(p.getId(), p);
            return p;
        });
        when(projectRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long uid = inv.getArgument(1);
            Project p = projects.get(id);
            if (p == null || !p.getUserId().equals(uid) || p.getDeletedAtUtc() != null) return Optional.empty();
            return Optional.of(p);
        });
        when(projectRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(anyLong())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            return projects.values().stream()
                    .filter(p -> p.getUserId().equals(uid) && p.getDeletedAtUtc() == null)
                    .toList();
        });

        when(milestoneRepository.save(any(ProjectMilestone.class))).thenAnswer(inv -> {
            ProjectMilestone m = inv.getArgument(0);
            if (m.getId() == null) m.setId(seq.getAndIncrement());
            milestones.put(m.getId(), m);
            return m;
        });
        when(milestoneRepository.findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderBySortOrderAscIdAsc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long pid = inv.getArgument(1);
                    return milestones.values().stream()
                            .filter(m -> m.getUserId().equals(uid) && m.getProjectId().equals(pid) && m.getDeletedAtUtc() == null)
                            .toList();
                });
        when(milestoneRepository.findByIdAndUserIdAndProjectIdAndDeletedAtUtcIsNull(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    Long uid = inv.getArgument(1);
                    Long pid = inv.getArgument(2);
                    ProjectMilestone m = milestones.get(id);
                    if (m == null || !m.getUserId().equals(uid) || !m.getProjectId().equals(pid) || m.getDeletedAtUtc() != null) {
                        return Optional.empty();
                    }
                    return Optional.of(m);
                });

        when(worklogRepository.save(any(ProjectWorklog.class))).thenAnswer(inv -> {
            ProjectWorklog w = inv.getArgument(0);
            if (w.getId() == null) w.setId(seq.getAndIncrement());
            worklogs.put(w.getId(), w);
            return w;
        });
        when(worklogRepository.findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderByWorkDateDescIdDesc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long pid = inv.getArgument(1);
                    return worklogs.values().stream()
                            .filter(w -> w.getUserId().equals(uid) && w.getProjectId().equals(pid) && w.getDeletedAtUtc() == null)
                            .sorted((a, b) -> {
                                int cmp = b.getWorkDate().compareTo(a.getWorkDate());
                                return cmp != 0 ? cmp : b.getId().compareTo(a.getId());
                            })
                            .toList();
                });

        when(mediaRepository.save(any(ProjectMedia.class))).thenAnswer(inv -> {
            ProjectMedia m = inv.getArgument(0);
            if (m.getId() == null) m.setId(seq.getAndIncrement());
            mediaMap.put(m.getId(), m);
            return m;
        });
        when(mediaRepository.findAllByUserIdAndProjectIdOrderByCreatedAtUtcDescIdDesc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long pid = inv.getArgument(1);
                    return mediaMap.values().stream()
                            .filter(m -> m.getUserId().equals(uid) && m.getProjectId().equals(pid))
                            .toList();
                });
        when(mediaRepository.countByUserIdAndProjectIdAndMediaType(anyLong(), anyLong(), anyString()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long pid = inv.getArgument(1);
                    String type = inv.getArgument(2);
                    return mediaMap.values().stream()
                            .filter(m -> m.getUserId().equals(uid) && m.getProjectId().equals(pid) && type.equals(m.getMediaType()))
                            .count();
                });

        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> {
            RecordHistory h = inv.getArgument(0);
            histories.add(h);
            return h;
        });
        when(recordHistoryRepository.findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(
                anyLong(), anyString(), anyLong()))
                .thenAnswer(inv -> histories);
    }

    @Test
    void zeroMilestones_returnsNullProgressNotZero_andSupportsPathPilot25Percent() {
        ProjectDetailDto created = projectService.createProject(1L, new ProjectRequest(
                "PathPilot",
                "rocket",
                "Personal career tracker",
                List.of("Java", "Spring Boot", "React"),
                "https://pathpilot.example.com",
                "https://github.com/user/pathpilot",
                "CURRENT",
                LocalDate.of(2026, 11, 1),
                null
        ));

        assertNull(created.project().progress());
        assertEquals(0, created.project().totalMilestones());

        Long projectId = created.project().id();
        ProjectMilestoneDto m1 = projectService.createMilestone(1L, projectId,
                new ProjectMilestoneRequest("Milestone 1", LocalDate.of(2026, 11, 10), null, "PLANNED", 1, null));
        projectService.createMilestone(1L, projectId,
                new ProjectMilestoneRequest("Milestone 2", LocalDate.of(2026, 11, 15), null, "PLANNED", 2, null));
        projectService.createMilestone(1L, projectId,
                new ProjectMilestoneRequest("Milestone 3", LocalDate.of(2026, 11, 20), null, "PLANNED", 3, null));
        projectService.createMilestone(1L, projectId,
                new ProjectMilestoneRequest("Milestone 4", LocalDate.of(2026, 11, 25), null, "PLANNED", 4, null));

        ProjectMilestoneDto completed = projectService.toggleMilestoneCompletion(
                1L, projectId, m1.id(), new MilestoneCompleteRequest(true, null)
        );
        assertEquals("COMPLETED", completed.status());
        assertNotNull(completed.completedDate());

        ProjectDetailDto detail = projectService.getProjectDetail(1L, projectId);
        assertEquals(4, detail.project().totalMilestones());
        assertEquals(1, detail.project().completedMilestones());
        assertEquals(25.0, detail.project().progress());

        // Status change keeps history
        ProjectDetailDto statusUpdated = projectService.updateProjectStatus(
                1L, projectId, new ProjectStatusRequest("PAUSED", "Exam week")
        );
        assertEquals("PAUSED", statusUpdated.project().status());
        assertTrue(statusUpdated.statusHistory().stream().anyMatch(h -> "STATUS_CHANGE".equals(h.actionType())));
    }

    @Test
    void userIsolation_andUrlValidation_areEnforced() {
        ProjectDetailDto created = projectService.createProject(1L, new ProjectRequest(
                "Private App", "code", null, List.of("Java"), null, null, "PLANNED", null, null
        ));

        ApiException notFound = assertThrows(ApiException.class,
                () -> projectService.getProjectDetail(2L, created.project().id()));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());

        ApiException badUrl = assertThrows(ApiException.class, () -> projectService.createProject(1L, new ProjectRequest(
                "Bad Link", "code", null, List.of(), "ftp://bad.example.com", null, "PLANNED", null, null
        )));
        assertEquals(HttpStatus.BAD_REQUEST, badUrl.getStatus());
    }

    @Test
    void worklogsOrderedNewestFirst() {
        Long projectId = projectService.createProject(1L, new ProjectRequest(
                "Worklog App", "code", null, List.of(), null, null, "CURRENT", null, null
        )).project().id();

        projectService.createWorklog(1L, projectId,
                new ProjectWorklogRequest(LocalDate.of(2026, 11, 20), 45, "Older entry"));
        projectService.createWorklog(1L, projectId,
                new ProjectWorklogRequest(LocalDate.of(2026, 11, 25), 90, "Newer entry"));

        ProjectDetailDto detail = projectService.getProjectDetail(1L, projectId);
        assertEquals(2, detail.worklogs().size());
        assertEquals("Newer entry", detail.worklogs().get(0).summary());
        assertEquals("Older entry", detail.worklogs().get(1).summary());
    }

    @Test
    void mediaUpload_validatesMagicBytesSizeCountAndRandomFilename() {
        Long projectId = projectService.createProject(1L, new ProjectRequest(
                "Media App", "code", null, List.of(), null, null, "CURRENT", null, null
        )).project().id();

        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile validPng = new MockMultipartFile(
                "file", "../../secret.png", "image/png", pngHeader
        );
        ProjectMediaDto saved = projectService.uploadScreenshot(1L, projectId, validPng, "Home screen");
        assertEquals("SCREENSHOT", saved.mediaType());
        assertNotNull(saved.fileUrl());
        ProjectMedia storedEntity = mediaMap.get(saved.id());
        assertNotEquals("secret.png", storedEntity.getStoredFilename());
        assertTrue(storedEntity.getStoredFilename().endsWith(".png"));

        // Fake PNG (actually HTML) rejected
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)
        );
        ApiException invalidType = assertThrows(ApiException.class,
                () -> projectService.uploadScreenshot(1L, projectId, spoofed, null));
        assertEquals("INVALID_FILE_TYPE", invalidType.getCode());

        // Oversized (> 2 MB) rejected
        byte[] tooBig = new byte[(2 * 1024 * 1024) + 10];
        System.arraycopy(pngHeader, 0, tooBig, 0, pngHeader.length);
        MockMultipartFile hugeFile = new MockMultipartFile("file", "big.png", "image/png", tooBig);
        ApiException tooLarge = assertThrows(ApiException.class,
                () -> projectService.uploadScreenshot(1L, projectId, hugeFile, null));
        assertEquals("FILE_TOO_LARGE", tooLarge.getCode());

        // Upload 5 more valid PNGs (total 6) -> 7th rejected
        for (int i = 0; i < 5; i++) {
            projectService.uploadScreenshot(1L, projectId,
                    new MockMultipartFile("file", "shot" + i + ".png", "image/png", pngHeader), null);
        }
        ApiException maxLimit = assertThrows(ApiException.class, () -> projectService.uploadScreenshot(
                1L, projectId, new MockMultipartFile("file", "shot7.png", "image/png", pngHeader), null
        ));
        assertEquals("MAX_SCREENSHOTS_EXCEEDED", maxLimit.getCode());
    }
}
