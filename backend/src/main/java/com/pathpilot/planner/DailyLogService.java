package com.pathpilot.planner;

import com.pathpilot.planner.dto.DailyLogDtos.DailyLogRequest;
import com.pathpilot.planner.dto.DailyLogDtos.DailyLogResponse;
import com.pathpilot.planner.dto.DailyLogDtos.LearningRecordDto;
import com.pathpilot.planner.dto.DailyLogDtos.LearningRecordRequest;
import com.pathpilot.planner.dto.DailyLogDtos.RestDayRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyLogService {

    private final DailyLogRepository dailyLogRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final RecordHistoryRepository recordHistoryRepository;

    public DailyLogService(
            DailyLogRepository dailyLogRepository,
            LearningRecordRepository learningRecordRepository,
            RecordHistoryRepository recordHistoryRepository
    ) {
        this.dailyLogRepository = dailyLogRepository;
        this.learningRecordRepository = learningRecordRepository;
        this.recordHistoryRepository = recordHistoryRepository;
    }

    @Transactional(readOnly = true)
    public DailyLogResponse getByDate(Long userId, LocalDate date) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, date).orElse(null);
        List<LearningRecordDto> records = listLearningRecords(userId, date);
        int totalMinutes = records.stream().mapToInt(LearningRecordDto::minutesSpent).sum();

        return new DailyLogResponse(
                date,
                log != null ? log.getDayStatus() : "UNRECORDED",
                log != null && log.getReflectionNote() != null ? log.getReflectionNote() : "",
                records,
                totalMinutes
        );
    }

    @Transactional
    public DailyLogResponse saveNote(Long userId, LocalDate date, DailyLogRequest request) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, date).orElseGet(() -> {
            DailyLog created = new DailyLog();
            created.setUserId(userId);
            created.setLogDate(date);
            created.setDayStatus("RECORDED");
            created.setDataSource("MANUAL");
            return created;
        });

        String oldNote = log.getReflectionNote();
        String newNote = request.reflectionNote() != null ? request.reflectionNote().trim() : "";

        if (log.getId() != null && oldNote != null && !oldNote.equals(newNote)) {
            RecordHistory history = new RecordHistory();
            history.setUserId(userId);
            history.setEntityType("DAILY_LOG");
            history.setEntityId(log.getId());
            history.setActionType("UPDATED");
            history.setActorSource("MANUAL");
            history.setRecordDate(date);
            history.setChangeSummary("Old note: \"" + oldNote + "\"");
            recordHistoryRepository.save(history);
        }

        if (!"REST_DAY".equalsIgnoreCase(log.getDayStatus())) {
            log.setDayStatus("RECORDED");
        }
        log.setReflectionNote(newNote);
        dailyLogRepository.save(log);

        return getByDate(userId, date);
    }

    @Transactional
    public DailyLogResponse confirmRestDay(Long userId, LocalDate date, RestDayRequest request) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, date).orElseGet(() -> {
            DailyLog created = new DailyLog();
            created.setUserId(userId);
            created.setLogDate(date);
            created.setDataSource("MANUAL");
            return created;
        });

        log.setDayStatus("REST_DAY");
        if (request != null && request.note() != null && !request.note().isBlank()) {
            log.setReflectionNote(request.note().trim());
        }
        DailyLog saved = dailyLogRepository.save(log);

        RecordHistory history = new RecordHistory();
        history.setUserId(userId);
        history.setEntityType("DAILY_LOG");
        history.setEntityId(saved.getId());
        history.setActionType("REST_DAY_CONFIRMED");
        history.setActorSource("MANUAL");
        history.setRecordDate(date);
        history.setChangeSummary("Confirmed rest day for " + date);
        recordHistoryRepository.save(history);

        return getByDate(userId, date);
    }

    @Transactional(readOnly = true)
    public List<LearningRecordDto> listLearningRecords(Long userId, LocalDate date) {
        return learningRecordRepository
                .findAllByUserIdAndRecordDateOrderByCreatedAtUtcAscIdAsc(userId, date)
                .stream()
                .map(r -> new LearningRecordDto(
                        r.getId(),
                        r.getRecordDate(),
                        r.getTopic(),
                        r.getMinutesSpent(),
                        r.getNotes(),
                        r.getDataSource()
                ))
                .toList();
    }

    @Transactional
    public LearningRecordDto createLearningRecord(Long userId, LearningRecordRequest request) {
        LearningRecord record = new LearningRecord();
        record.setUserId(userId);
        record.setRecordDate(request.recordDate());
        record.setTopic(request.topic().trim());
        record.setMinutesSpent(request.minutesSpent());
        record.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);
        record.setDataSource("MANUAL");
        LearningRecord saved = learningRecordRepository.save(record);

        if (dailyLogRepository.findByUserIdAndLogDate(userId, request.recordDate()).isEmpty()) {
            DailyLog log = new DailyLog();
            log.setUserId(userId);
            log.setLogDate(request.recordDate());
            log.setDayStatus("RECORDED");
            log.setDataSource("MANUAL");
            dailyLogRepository.save(log);
        }

        return new LearningRecordDto(
                saved.getId(),
                saved.getRecordDate(),
                saved.getTopic(),
                saved.getMinutesSpent(),
                saved.getNotes(),
                saved.getDataSource()
        );
    }
}
