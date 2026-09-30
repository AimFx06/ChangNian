package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.DailyStatusEntity;
import com.changnian.domain.entity.ElderResponseEntity;
import com.changnian.domain.entity.EmergencyEventEntity;
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
import com.changnian.domain.mapper.EmergencyEventMapper;
import com.changnian.domain.mapper.FamilyMemoryMapper;
import com.changnian.domain.mapper.MedicalRecordMapper;
import com.changnian.domain.mapper.MedicationRecordMapper;
import com.changnian.domain.mapper.MedicationScheduleMapper;
import com.changnian.domain.mapper.MicrophoneRequestMapper;
import com.changnian.domain.mapper.TopicCardMapper;
import com.changnian.domain.mapper.UserMapper;
import com.changnian.domain.mapper.WeeklyReportMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class HomeService {
    private final WeeklyReportMapper weeklyReportMapper;
    private final DailyStatusMapper dailyStatusMapper;
    private final TopicCardMapper topicCardMapper;
    private final MicrophoneRequestMapper microphoneRequestMapper;
    private final ElderResponseMapper elderResponseMapper;
    private final MedicationScheduleMapper medicationScheduleMapper;
    private final EmergencyEventMapper emergencyEventMapper;
    private final UserMapper userMapper;
    private final FamilyMemoryMapper familyMemoryMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final MedicationRecordMapper medicationRecordMapper;

    public HomeService(
            WeeklyReportMapper weeklyReportMapper,
            DailyStatusMapper dailyStatusMapper,
            TopicCardMapper topicCardMapper,
            MicrophoneRequestMapper microphoneRequestMapper,
            ElderResponseMapper elderResponseMapper,
            MedicationScheduleMapper medicationScheduleMapper,
            EmergencyEventMapper emergencyEventMapper,
            UserMapper userMapper,
            FamilyMemoryMapper familyMemoryMapper,
            MedicalRecordMapper medicalRecordMapper,
            MedicationRecordMapper medicationRecordMapper
    ) {
        this.weeklyReportMapper = weeklyReportMapper;
        this.dailyStatusMapper = dailyStatusMapper;
        this.topicCardMapper = topicCardMapper;
        this.microphoneRequestMapper = microphoneRequestMapper;
        this.elderResponseMapper = elderResponseMapper;
        this.medicationScheduleMapper = medicationScheduleMapper;
        this.emergencyEventMapper = emergencyEventMapper;
        this.userMapper = userMapper;
        this.familyMemoryMapper = familyMemoryMapper;
        this.medicalRecordMapper = medicalRecordMapper;
        this.medicationRecordMapper = medicationRecordMapper;
    }

    public ChildHomeView childHome() {
        CurrentUser user = requireRole(Role.CHILD);
        WeeklyReportEntity weeklyReport = weeklyReportMapper.selectOne(Wrappers.<WeeklyReportEntity>lambdaQuery()
                .eq(WeeklyReportEntity::getFamilyId, user.familyId())
                .orderByDesc(WeeklyReportEntity::getCreatedAt)
                .last("LIMIT 1"));
        List<WeeklyReportEntity> weeklyReports = weeklyReportMapper.selectList(Wrappers.<WeeklyReportEntity>lambdaQuery()
                .eq(WeeklyReportEntity::getFamilyId, user.familyId())
                .orderByDesc(WeeklyReportEntity::getCreatedAt)
                .last("LIMIT 20"));
        List<DailyStatusEntity> dailyStatuses = dailyStatusMapper.selectList(Wrappers.<DailyStatusEntity>lambdaQuery()
                .eq(DailyStatusEntity::getFamilyId, user.familyId())
                .orderByDesc(DailyStatusEntity::getStatusDate)
                .last("LIMIT 30"));
        List<TopicCardEntity> topicCards = topicCardMapper.selectList(Wrappers.<TopicCardEntity>lambdaQuery()
                .eq(TopicCardEntity::getFamilyId, user.familyId())
                .eq(TopicCardEntity::getStatus, "ACTIVE")
                .orderByDesc(TopicCardEntity::getCreatedAt));
        List<ResponseView> responses = elderResponseMapper.selectList(Wrappers.<ElderResponseEntity>lambdaQuery()
                        .eq(ElderResponseEntity::getFamilyId, user.familyId())
                        .orderByDesc(ElderResponseEntity::getCreatedAt)
                        .last("LIMIT 60"))
                .stream()
                .map(this::toResponseView)
                .toList();
        List<MedicationScheduleEntity> medications = medicationScheduleMapper.selectList(Wrappers.<MedicationScheduleEntity>lambdaQuery()
                .eq(MedicationScheduleEntity::getFamilyId, user.familyId())
                .eq(MedicationScheduleEntity::getEnabled, true)
                .orderByAsc(MedicationScheduleEntity::getRemindTime));
        List<MedicationRecordEntity> medicationRecords = medicationRecordMapper.selectList(Wrappers.<MedicationRecordEntity>lambdaQuery()
                .eq(MedicationRecordEntity::getFamilyId, user.familyId())
                .ge(MedicationRecordEntity::getRecordDate, LocalDate.now().withDayOfMonth(1))
                .orderByDesc(MedicationRecordEntity::getRecordDate)
                .orderByDesc(MedicationRecordEntity::getActionTime));
        List<EmergencyEventEntity> emergencies = emergencyEventMapper.selectList(Wrappers.<EmergencyEventEntity>lambdaQuery()
                .eq(EmergencyEventEntity::getFamilyId, user.familyId())
                .eq(EmergencyEventEntity::getStatus, "ACTIVE")
                .orderByDesc(EmergencyEventEntity::getCreatedAt)
                .last("LIMIT 10"));
        List<ElderView> elders = userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getFamilyId, user.familyId())
                        .eq(UserEntity::getRole, Role.ELDER.name())
                        .orderByAsc(UserEntity::getId))
                .stream()
                .map(ElderView::from)
                .toList();
        List<MemoryView> memories = familyMemoryMapper.selectList(Wrappers.<FamilyMemoryEntity>lambdaQuery()
                        .eq(FamilyMemoryEntity::getFamilyId, user.familyId())
                        .orderByDesc(FamilyMemoryEntity::getCreatedAt)
                        .last("LIMIT 40"))
                .stream()
                .map(memory -> new MemoryView(memory.getTitle(), memory.getContent(), memory.getCreatedAt().toString(), memory.getElderId()))
                .toList();
        List<MedicalRecordEntity> medicalRecords = medicalRecordMapper.selectList(Wrappers.<MedicalRecordEntity>lambdaQuery()
                .eq(MedicalRecordEntity::getFamilyId, user.familyId())
                .orderByDesc(MedicalRecordEntity::getCreatedAt)
                .last("LIMIT 20"));
        return new ChildHomeView(weeklyReport, weeklyReports, dailyStatuses, topicCards, responses, memories, medications, medicationRecords, medicalRecords, emergencies, elders);
    }

    public ElderHomeView elderHome() {
        CurrentUser user = requireRole(Role.ELDER);
        List<PendingRequestView> pendingRequests = microphoneRequestMapper.selectList(Wrappers.<MicrophoneRequestEntity>lambdaQuery()
                        .eq(MicrophoneRequestEntity::getFamilyId, user.familyId())
                        .eq(MicrophoneRequestEntity::getElderId, user.id())
                        .eq(MicrophoneRequestEntity::getStatus, "PENDING")
                        .orderByDesc(MicrophoneRequestEntity::getCreatedAt))
                .stream()
                .map(request -> {
                    TopicCardEntity topic = request.getTopicId() == null ? null : topicCardMapper.selectById(request.getTopicId());
                    UserEntity child = userMapper.selectById(request.getChildId());
                    normalizePromptText(request, topic, child);
                    return new PendingRequestView(
                            request,
                            topic,
                            MemberView.from(child)
                    );
                })
                .toList();
        List<MedicationScheduleEntity> medications = medicationScheduleMapper.selectList(Wrappers.<MedicationScheduleEntity>lambdaQuery()
                .eq(MedicationScheduleEntity::getFamilyId, user.familyId())
                .eq(MedicationScheduleEntity::getElderId, user.id())
                .eq(MedicationScheduleEntity::getEnabled, true)
                .orderByAsc(MedicationScheduleEntity::getRemindTime));
        UserEntity child = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getFamilyId, user.familyId())
                .eq(UserEntity::getRole, Role.CHILD.name())
                .orderByAsc(UserEntity::getId)
                .last("LIMIT 1"));
        return new ElderHomeView(pendingRequests, medications, MemberView.from(child));
    }

    public UserEntity firstElder(Long familyId) {
        UserEntity elder = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getFamilyId, familyId)
                .eq(UserEntity::getRole, Role.ELDER.name())
                .orderByAsc(UserEntity::getId)
                .last("LIMIT 1"));
        if (elder == null) {
            throw new BizException("当前家庭还没有绑定老人账号");
        }
        return elder;
    }

    private ResponseView toResponseView(ElderResponseEntity response) {
        MicrophoneRequestEntity request = response.getRequestId() == null ? null : microphoneRequestMapper.selectById(response.getRequestId());
        TopicCardEntity topic = request == null || request.getTopicId() == null ? null : topicCardMapper.selectById(request.getTopicId());
        return new ResponseView(response, request, topic);
    }

    private void normalizePromptText(MicrophoneRequestEntity request, TopicCardEntity topic, UserEntity child) {
        if (request.getPromptText() != null) {
            request.setPromptText(normalizeElderPronouns(request.getPromptText()));
        }
        if (topic == null || request.getPromptText() == null || !isTopicExplanationPrompt(request.getPromptText(), topic)) {
            return;
        }
        request.setPromptText(localElderPrompt(child, topic));
    }

    private boolean isTopicExplanationPrompt(String promptText, TopicCardEntity topic) {
        String prompt = promptText.trim();
        if (prompt.contains("挺惦记你这件事")) {
            return true;
        }
        String title = topic.getTitle() == null ? "" : topic.getTitle().trim();
        String content = topic.getContent() == null ? "" : topic.getContent().trim();
        String rawTopicText = title.isBlank() ? content : content.isBlank() ? title : title + "，" + content;
        if (!rawTopicText.isBlank() && prompt.equals(rawTopicText)) {
            return true;
        }
        if (!title.isBlank() && prompt.contains(title) && prompt.length() > 45) {
            return true;
        }
        String contentHead = content.length() <= 18 ? content : content.substring(0, 18);
        return !contentHead.isBlank() && prompt.contains(contentHead);
    }

    private String localElderPrompt(UserEntity child, TopicCardEntity topic) {
        String childName = child == null || child.getDisplayName() == null || child.getDisplayName().isBlank() ? "孩子" : child.getDisplayName().trim();
        String title = topic.getTitle() == null ? "" : topic.getTitle().trim();
        String content = topic.getContent() == null ? "" : topic.getContent().trim();
        String source = title + "，" + content;
        if (source.contains("晚上好")) {
            return childName + "想接着和你聊聊：晚上在忙什么呀？";
        }
        if (source.contains("睡") || source.contains("精神")) {
            return childName + "想问问你：最近睡得怎么样，精神还好吗？";
        }
        if (source.contains("药")) {
            return childName + "想问问你：今天吃药和身体感觉怎么样？";
        }
        if (source.contains("晚饭") || source.contains("吃饭") || source.contains("买菜")) {
            return childName + "想听你说说：今天吃了什么，过得怎么样？";
        }
        if (!title.isBlank()) {
            String shortTitle = title.length() <= 28 ? title : title.substring(0, 28);
            return childName + "想听你说说：" + normalizeElderPronouns(shortTitle) + "。";
        }
        return childName + "想听你说说今天过得怎么样。";
    }

    private String normalizeElderPronouns(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text
                .replace("她看了吗", "你看了吗")
                .replace("他看了吗", "你看了吗")
                .replace("她看了没", "你看了没")
                .replace("他看了没", "你看了没")
                .replace("她想不想", "你想不想")
                .replace("他想不想", "你想不想")
                .replace("问问她", "问问你")
                .replace("问问他", "问问你")
                .replace("妈妈最近", "你最近")
                .replace("爸爸最近", "你最近")
                .replace("妈妈这几天", "你这几天")
                .replace("爸爸这几天", "你这几天");
    }

    private CurrentUser requireRole(Role role) {
        CurrentUser user = UserContext.get();
        if (!Objects.equals(user.role(), role)) {
            throw new BizException("当前账号不能访问该页面数据");
        }
        return user;
    }

    public record ChildHomeView(
            WeeklyReportEntity weeklyReport,
            List<WeeklyReportEntity> weeklyReports,
            List<DailyStatusEntity> dailyStatuses,
            List<TopicCardEntity> topicCards,
            List<ResponseView> responses,
            List<MemoryView> memories,
            List<MedicationScheduleEntity> medications,
            List<MedicationRecordEntity> medicationRecords,
            List<MedicalRecordEntity> medicalRecords,
            List<EmergencyEventEntity> emergencies,
            List<ElderView> elders
    ) {
    }

    public record ElderHomeView(List<PendingRequestView> pendingRequests, List<MedicationScheduleEntity> medications, MemberView child) {
    }

    public record PendingRequestView(MicrophoneRequestEntity request, TopicCardEntity topic, MemberView child) {
    }

    public record ResponseView(ElderResponseEntity response, MicrophoneRequestEntity request, TopicCardEntity topic) {
    }

    public record MemoryView(String title, String content, String timeText, Long elderId) {
    }

    public record ElderView(Long id, String username, String displayName, String phone) {
        public static ElderView from(UserEntity user) {
            return new ElderView(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhone());
        }
    }

    public record MemberView(Long id, String username, String displayName, String phone, String gender) {
        public static MemberView from(UserEntity user) {
            if (user == null) {
                return null;
            }
            return new MemberView(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhone(), user.getGender());
        }
    }
}
