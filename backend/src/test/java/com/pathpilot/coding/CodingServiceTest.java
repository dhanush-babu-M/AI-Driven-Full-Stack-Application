package com.pathpilot.coding;

import com.pathpilot.coding.dto.CodingDtos.CodingActivityDto;
import com.pathpilot.coding.dto.CodingDtos.CodingActivityRequest;
import com.pathpilot.coding.dto.CodingDtos.CodingStreakSummaryResponse;
import com.pathpilot.coding.dto.CodingDtos.DayActivityDetailResponse;
import com.pathpilot.coding.dto.CodingDtos.HeatmapResponse;
import com.pathpilot.common.ApiException;
import com.pathpilot.github.GithubEvent;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.planner.LearningRecord;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingServiceTest {

    private CodingActivityRepository codingActivityRepository;
    private GithubEventRepository githubEventRepository;
    private LearningRecordRepository learningRecordRepository;
    private RecordHistoryRepository recordHistoryRepository;
    private UserProfileRepository userProfileRepository;
    private CodingService codingService;

    private final Map<Long, CodingActivity> activities = new ConcurrentHashMap<>();
    private final List<GithubEvent> events = new ArrayList<>();
    private final List<LearningRecord> learnings = new ArrayList<>();
    private final List<RecordHistory> histories = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        codingActivityRepository = mock(CodingActivityRepository.class);
        githubEventRepository = mock(GithubEventRepository.class);
        learningRecordRepository = mock(LearningRecordRepository.class);
        recordHistoryRepository = mock(RecordHistoryRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);

        codingService = new CodingService(
                codingActivityRepository,
                githubEventRepository,
                learningRecordRepository,
                recordHistoryRepository,
                userProfileRepository
        );

        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setTimeZone("UTC");
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        when(codingActivityRepository.save(any(CodingActivity.class))).thenAnswer(inv -> {
            CodingActivity a = inv.getArgument(0);
            if (a.getId() == null) a.setId(seq.getAndIncrement());
            activities.put(a.getId(), a);
            return a;
        });

        when(codingActivityRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    Long uid = inv.getArgument(1);
                    CodingActivity found = activities.get(id);
                    if (found == null || !found.getUserId().equals(uid) || found.getDeletedAtUtc() != null) {
                        return Optional.empty();
                    }
                    return Optional.of(found);
                });

        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate s = inv.getArgument(1);
                    LocalDate e = inv.getArgument(2);
                    return activities.values().stream()
                            .filter(a -> a.getUserId().equals(uid) && a.getDeletedAtUtc() == null
                                    && !a.getActivityDate().isBefore(s) && !a.getActivityDate().isAfter(e))
                            .toList();
                });

        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByActivityDateDescIdDesc(anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    return activities.values().stream()
                            .filter(a -> a.getUserId().equals(uid) && a.getDeletedAtUtc() == null)
                            .sorted((a, b) -> b.getActivityDate().compareTo(a.getActivityDate()))
                            .toList();
                });

        when(codingActivityRepository.findAllByUserIdAndActivityDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcAscIdAsc(
                anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate d = inv.getArgument(1);
                    return activities.values().stream()
                            .filter(a -> a.getUserId().equals(uid) && a.getDeletedAtUtc() == null && a.getActivityDate().equals(d))
                            .toList();
                });

        when(githubEventRepository.findAllByUserIdAndEventDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> events);
        when(githubEventRepository.findAllByUserIdAndEventDateOrderByOccurredAtUtcDescIdDesc(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> events);
        when(learningRecordRepository.findAllByUserIdAndRecordDateOrderByCreatedAtUtcAscIdAsc(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> learnings);

        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> {
            RecordHistory h = inv.getArgument(0);
            histories.add(h);
            return h;
        });
    }

    @Test
    void createUpdateDelete_writesAuditHistoryAndEnforcesUserIsolation() {
        CodingActivityDto created = codingService.create(1L, new CodingActivityRequest(
                LocalDate.of(2026, 11, 20),
                "LeetCode",
                "Two Sum",
                "HashMap",
                "Easy",
                "Solved",
                1,
                25,
                null,
                "Used complement map"
        ));

        assertEquals("MANUAL", created.dataSource());
        assertEquals("Solved", created.result());
        assertEquals(1, histories.size());
        assertEquals("CREATE", histories.get(0).getActionType());

        // Another user cannot edit or delete user 1's entry
        ApiException forbidden = assertThrows(ApiException.class, () -> codingService.delete(2L, created.id()));
        assertEquals(HttpStatus.NOT_FOUND, forbidden.getStatus());

        codingService.update(1L, created.id(), new CodingActivityRequest(
                LocalDate.of(2026, 11, 20),
                "LeetCode",
                "Two Sum",
                "Arrays",
                "Easy",
                "Practiced",
                1,
                30,
                null,
                "Revisited two-pointer variant"
        ));
        assertEquals("UPDATE", histories.get(1).getActionType());

        codingService.delete(1L, created.id());
        assertEquals("DELETE", histories.get(2).getActionType());
        assertTrue(codingService.listActivities(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                null, null, null, 0, 10).items().isEmpty());
    }

    @Test
    void heatmapAndDayDetails_keepGithubAndManualSourcesSeparate() {
        LocalDate day = LocalDate.of(2026, 11, 15);
        codingService.create(1L, new CodingActivityRequest(
                day, "CodingBat", "Warmup-1 sleepIn", "Recursion", "Easy", "Solved", 1, 10, null, "Base case"
        ));

        GithubEvent evt = new GithubEvent();
        evt.setUserId(1L);
        evt.setExternalEventId("gh-1");
        evt.setEventType("PushEvent");
        evt.setRepoName("user/repo");
        evt.setEventDate(day);
        evt.setOccurredAtUtc(Instant.parse("2026-11-15T12:00:00Z"));
        evt.setCommitCount(3);
        evt.setDataSource("IMPORTED");
        events.add(evt);

        HeatmapResponse combined = codingService.getHeatmap(1L, day, day, "combined");
        assertEquals("BOTH", combined.days().get(0).combinedCategory());
        assertEquals(3, combined.days().get(0).githubCommitCount());
        assertEquals(1, combined.days().get(0).manualCount());

        HeatmapResponse manualOnly = codingService.getHeatmap(1L, day, day, "manual");
        assertEquals("MANUAL_ONLY", manualOnly.days().get(0).combinedCategory());
        assertEquals(0, manualOnly.days().get(0).githubCount());

        DayActivityDetailResponse detail = codingService.getDayDetails(1L, day);
        assertEquals(1, detail.githubEvents().size());
        assertEquals(1, detail.codingActivities().size());

        CodingStreakSummaryResponse summary = codingService.getStreakAndSummary(
                1L, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 16)
        );
        assertEquals(1, summary.totalSolved());
        assertEquals("RULE", summary.banner().source());
    }
}
