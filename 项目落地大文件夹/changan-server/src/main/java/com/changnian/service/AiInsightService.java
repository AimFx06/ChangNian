package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.DailyStatusEntity;
import com.changnian.domain.entity.ElderResponseEntity;
import com.changnian.domain.entity.FamilyMemoryEntity;
import com.changnian.domain.entity.MedicalRecordEntity;
import com.changnian.domain.entity.MedicationRecordEntity;
import com.changnian.domain.entity.MedicationScheduleEntity;
import com.changnian.domain.entity.MicrophoneRequestEntity;
import com.changnian.domain.entity.TopicCardEntity;
import com.changnian.domain.entity.UserEntity;
import com.changnian.domain.entity.WeeklyReportEntity;
import com.changnian.domain.mapper.DailyStatusMapper;
import com.changnian.domain.mapper.ElderResponseMapper;
import com.changnian.domain.mapper.FamilyMemoryMapper;
import com.changnian.domain.mapper.MedicalRecordMapper;
import com.changnian.domain.mapper.MedicationRecordMapper;
import com.changnian.domain.mapper.MedicationScheduleMapper;
import com.changnian.domain.mapper.MicrophoneRequestMapper;
import com.changnian.domain.mapper.TopicCardMapper;
import com.changnian.domain.mapper.UserMapper;
import com.changnian.domain.mapper.WeeklyReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiInsightService {
    private final AiService aiService;
    private final MemoryService memoryService;
    private final UserMapper userMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final DailyStatusMapper dailyStatusMapper;
    private final TopicCardMapper topicCardMapper;
    private final ElderResponseMapper elderResponseMapper;
    private final MicrophoneRequestMapper microphoneRequestMapper;
    private final MedicationScheduleMapper medicationScheduleMapper;
    private final MedicationRecordMapper medicationRecordMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final FamilyMemoryMapper familyMemoryMapper;

    public AiInsightService(
            AiService aiService,
            MemoryService memoryService,
            UserMapper userMapper,
            WeeklyReportMapper weeklyReportMapper,
            DailyStatusMapper dailyStatusMapper,
            TopicCardMapper topicCardMapper,
            ElderResponseMapper elderResponseMapper,
            MicrophoneRequestMapper microphoneRequestMapper,
            MedicationScheduleMapper medicationScheduleMapper,
            MedicationRecordMapper medicationRecordMapper,
            MedicalRecordMapper medicalRecordMapper,
            FamilyMemoryMapper familyMemoryMapper
    ) {
        this.aiService = aiService;
        this.memoryService = memoryService;
        this.userMapper = userMapper;
        this.weeklyReportMapper = weeklyReportMapper;
        this.dailyStatusMapper = dailyStatusMapper;
        this.topicCardMapper = topicCardMapper;
        this.elderResponseMapper = elderResponseMapper;
        this.microphoneRequestMapper = microphoneRequestMapper;
        this.medicationScheduleMapper = medicationScheduleMapper;
        this.medicationRecordMapper = medicationRecordMapper;
        this.medicalRecordMapper = medicalRecordMapper;
        this.familyMemoryMapper = familyMemoryMapper;
    }

    @Transactional
    public InsightRefreshResult refreshCurrentElder(String elderUsername) {
        CurrentUser user = UserContext.get();
        if (user.role() != Role.CHILD) {
            throw new BizException("只有子女端可以刷新 AI 数据");
        }
        UserEntity elder = resolveElder(user.familyId(), elderUsername);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1);
        LocalDateTime signalStart = today.minusDays(7).atStartOfDay();
        List<String> signals = collectSignals(user.familyId(), elder, signalStart);

        AiService.AiWeeklyReportResult weekly = aiService.generateWeeklyReportCard(elder.getDisplayName(), signals);
        WeeklyReportEntity savedWeekly = saveWeeklyReport(user.familyId(), elder.getId(), today, weekly);

        AiService.AiDailyStatusResult daily = aiService.generateDailyStatuses(elder.getDisplayName(), startDate, today, signals);
        List<DailyStatusEntity> savedDaily = saveDailyStatuses(user.familyId(), elder.getId(), startDate, today, daily.statuses());

        AiService.TopicRefreshResult topics = aiService.refreshTopicCards(signals, elder.getUsername());
        List<FamilyMemoryEntity> savedMemories = refreshMemories(user.familyId(), elder);
        boolean mocked = weekly.mocked() || daily.mocked() || topics.mocked() || !aiService.isDeepSeekConfigured();

        return new InsightRefreshResult(savedWeekly, savedDaily, topics.topics(), savedMemories, mocked, aiService.isDeepSeekConfigured());
    }

    private UserEntity resolveElder(Long familyId, String elderUsername) {
        UserEntity elder = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getFamilyId, familyId)
                .eq(UserEntity::getRole, Role.ELDER.name())
                .eq(elderUsername != null && !elderUsername.isBlank(), UserEntity::getUsername, elderUsername == null ? "" : elderUsername.trim())
                .orderByAsc(UserEntity::getId)
                .last("LIMIT 1"));
        if (elder == null) {
            throw new BizException("选择的老人账号不存在");
        }
        return elder;
    }

    private List<String> collectSignals(Long familyId, UserEntity elder, LocalDateTime signalStart) {
        List<String> signals = new ArrayList<>();
        signals.add("当前日期：" + LocalDate.now());
        signals.add("当前对象：" + elder.getDisplayName() + "，账号：" + elder.getUsername() + "，性别：" + safe(elder.getGender(), "未填写"));

        elderResponseMapper.selectList(Wrappers.<ElderResponseEntity>lambdaQuery()
                        .eq(ElderResponseEntity::getFamilyId, familyId)
                        .eq(ElderResponseEntity::getElderId, elder.getId())
                        .ge(ElderResponseEntity::getCreatedAt, signalStart)
                        .orderByDesc(ElderResponseEntity::getCreatedAt)
                        .last("LIMIT 30"))
                .forEach(response -> signals.add("最近留言：" + timeText(response.getCreatedAt()) + "，来源：" + response.getSourceType() + "，内容：" + safe(response.getContent(), "录音回应")));

        dailyStatusMapper.selectList(Wrappers.<DailyStatusEntity>lambdaQuery()
                        .eq(DailyStatusEntity::getFamilyId, familyId)
                        .eq(DailyStatusEntity::getElderId, elder.getId())
                        .orderByDesc(DailyStatusEntity::getStatusDate)
                        .last("LIMIT 14"))
                .forEach(status -> signals.add("已有近况：" + status.getStatusDate() + "，" + status.getTitle() + "，" + status.getContent()));

        familyMemoryMapper.selectList(Wrappers.<FamilyMemoryEntity>lambdaQuery()
                        .eq(FamilyMemoryEntity::getFamilyId, familyId)
                        .eq(FamilyMemoryEntity::getElderId, elder.getId())
                        .orderByDesc(FamilyMemoryEntity::getCreatedAt)
                        .last("LIMIT 20"))
                .forEach(memory -> signals.add("说过的事：" + memory.getTitle() + "，" + memory.getContent()));

        medicationScheduleMapper.selectList(Wrappers.<MedicationScheduleEntity>lambdaQuery()
                        .eq(MedicationScheduleEntity::getFamilyId, familyId)
                        .eq(MedicationScheduleEntity::getElderId, elder.getId())
                        .eq(MedicationScheduleEntity::getEnabled, true)
                        .orderByAsc(MedicationScheduleEntity::getRemindTime)
                        .last("LIMIT 10"))
                .forEach(schedule -> signals.add("用药提醒：" + schedule.getMedicineName() + "，" + schedule.getDosage() + "，" + schedule.getRemindTime() + "，备注：" + safe(schedule.getNote(), "无")));

        medicationRecordMapper.selectList(Wrappers.<MedicationRecordEntity>lambdaQuery()
                        .eq(MedicationRecordEntity::getFamilyId, familyId)
                        .eq(MedicationRecordEntity::getElderId, elder.getId())
                        .ge(MedicationRecordEntity::getRecordDate, LocalDate.now().withDayOfMonth(1))
                        .orderByDesc(MedicationRecordEntity::getRecordDate)
                        .orderByDesc(MedicationRecordEntity::getActionTime)
                        .last("LIMIT 20"))
                .forEach(record -> signals.add("用药记录：" + record.getRecordDate() + "，状态：" + record.getStatus() + "，时间：" + timeText(record.getActionTime())));

        medicalRecordMapper.selectList(Wrappers.<MedicalRecordEntity>lambdaQuery()
                        .eq(MedicalRecordEntity::getFamilyId, familyId)
                        .eq(MedicalRecordEntity::getElderId, elder.getId())
                        .orderByDesc(MedicalRecordEntity::getCreatedAt)
                        .last("LIMIT 10"))
                .forEach(record -> signals.add("就诊记录：" + timeText(record.getCreatedAt()) + "，类型：" + record.getRecordType() + "，标题：" + record.getTitle()));

        topicCardMapper.selectList(Wrappers.<TopicCardEntity>lambdaQuery()
                        .eq(TopicCardEntity::getFamilyId, familyId)
                        .eq(TopicCardEntity::getElderId, elder.getId())
                        .eq(TopicCardEntity::getStatus, "ACTIVE")
                        .orderByDesc(TopicCardEntity::getCreatedAt)
                        .last("LIMIT 10"))
                .forEach(topic -> signals.add("当前已有话题：" + topic.getCategory() + "，" + topic.getTitle() + "，" + topic.getContent()));

        signals.add("生成要求：请优先使用最近真实留言、用药记录、就诊记录和说过的事；父母数据必须按当前对象分开，不要混用。");
        return signals;
    }

    private WeeklyReportEntity saveWeeklyReport(Long familyId, Long elderId, LocalDate today, AiService.AiWeeklyReportResult weekly) {
        String label = weekLabel(today);
        WeeklyReportEntity report = weeklyReportMapper.selectOne(Wrappers.<WeeklyReportEntity>lambdaQuery()
                .eq(WeeklyReportEntity::getFamilyId, familyId)
                .eq(WeeklyReportEntity::getElderId, elderId)
                .eq(WeeklyReportEntity::getWeekLabel, label)
                .orderByDesc(WeeklyReportEntity::getCreatedAt)
                .last("LIMIT 1"));
        if (report == null) {
            report = new WeeklyReportEntity();
            report.setFamilyId(familyId);
            report.setElderId(elderId);
            report.setWeekLabel(label);
        }
        report.setTitle(weekly.title());
        report.setSummary(weekly.summary());
        report.setKeywords(weekly.keywords());
        report.setQuoteText(weekly.quoteText());
        report.setCreatedAt(LocalDateTime.now());
        if (report.getId() == null) {
            weeklyReportMapper.insert(report);
        } else {
            weeklyReportMapper.updateById(report);
        }
        return report;
    }

    private List<DailyStatusEntity> saveDailyStatuses(Long familyId, Long elderId, LocalDate startDate, LocalDate endDate, List<AiService.AiDailyStatus> statuses) {
        List<DailyStatusEntity> saved = new ArrayList<>();
        for (AiService.AiDailyStatus generated : statuses) {
            LocalDate date = parseDate(generated.statusDate());
            if (date == null || date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }
            DailyStatusEntity status = dailyStatusMapper.selectOne(Wrappers.<DailyStatusEntity>lambdaQuery()
                    .eq(DailyStatusEntity::getFamilyId, familyId)
                    .eq(DailyStatusEntity::getElderId, elderId)
                    .eq(DailyStatusEntity::getStatusDate, date)
                    .last("LIMIT 1"));
            if (status == null) {
                status = new DailyStatusEntity();
                status.setFamilyId(familyId);
                status.setElderId(elderId);
                status.setStatusDate(date);
            }
            status.setTitle(generated.title());
            status.setContent(generated.content());
            status.setTone(generated.tone());
            if (status.getId() == null) {
                dailyStatusMapper.insert(status);
            } else {
                dailyStatusMapper.updateById(status);
            }
            saved.add(status);
        }
        return saved;
    }

    private List<FamilyMemoryEntity> refreshMemories(Long familyId, UserEntity elder) {
        List<ElderResponseEntity> responses = elderResponseMapper.selectList(Wrappers.<ElderResponseEntity>lambdaQuery()
                .eq(ElderResponseEntity::getFamilyId, familyId)
                .eq(ElderResponseEntity::getElderId, elder.getId())
                .orderByDesc(ElderResponseEntity::getCreatedAt)
                .last("LIMIT 20"));
        if (aiService.isDeepSeekConfigured()) {
            for (ElderResponseEntity response : responses) {
                memoryService.regenerateFromResponse(response, topicForResponse(response));
            }
        } else {
            for (ElderResponseEntity response : responses) {
                memoryService.generateFromResponse(response, topicForResponse(response));
            }
        }
        return familyMemoryMapper.selectList(Wrappers.<FamilyMemoryEntity>lambdaQuery()
                .eq(FamilyMemoryEntity::getFamilyId, familyId)
                .eq(FamilyMemoryEntity::getElderId, elder.getId())
                .orderByDesc(FamilyMemoryEntity::getCreatedAt)
                .last("LIMIT 20"));
    }

    private TopicCardEntity topicForResponse(ElderResponseEntity response) {
        if (response.getRequestId() == null) {
            return null;
        }
        MicrophoneRequestEntity request = microphoneRequestMapper.selectById(response.getRequestId());
        if (request == null || request.getTopicId() == null) {
            return null;
        }
        return topicCardMapper.selectById(request.getTopicId());
    }

    private String weekLabel(LocalDate today) {
        int weekInMonth = ((today.getDayOfMonth() - 1) / 7) + 1;
        return today.getYear() + "年" + today.getMonthValue() + "月第" + weekInMonth + "周";
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String timeText(LocalDateTime value) {
        if (value == null) {
            return "未知时间";
        }
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record InsightRefreshResult(
            WeeklyReportEntity weeklyReport,
            List<DailyStatusEntity> dailyStatuses,
            List<TopicCardEntity> topics,
            List<FamilyMemoryEntity> memories,
            boolean mocked,
            boolean deepSeekConfigured
    ) {
    }
}
