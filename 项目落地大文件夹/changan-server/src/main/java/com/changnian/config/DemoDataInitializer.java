package com.changnian.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.domain.entity.DailyStatusEntity;
import com.changnian.domain.entity.ElderResponseEntity;
import com.changnian.domain.entity.FamilyEntity;
import com.changnian.domain.entity.FamilyMemoryEntity;
import com.changnian.domain.entity.MedicalRecordEntity;
import com.changnian.domain.entity.MedicationRecordEntity;
import com.changnian.domain.entity.MedicationScheduleEntity;
import com.changnian.domain.entity.TopicCardEntity;
import com.changnian.domain.entity.UserEntity;
import com.changnian.domain.entity.WeeklyReportEntity;
import com.changnian.domain.mapper.DailyStatusMapper;
import com.changnian.domain.mapper.ElderResponseMapper;
import com.changnian.domain.mapper.FamilyMapper;
import com.changnian.domain.mapper.FamilyMemoryMapper;
import com.changnian.domain.mapper.MedicalRecordMapper;
import com.changnian.domain.mapper.MedicationRecordMapper;
import com.changnian.domain.mapper.MedicationScheduleMapper;
import com.changnian.domain.mapper.TopicCardMapper;
import com.changnian.domain.mapper.UserMapper;
import com.changnian.domain.mapper.WeeklyReportMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private static final String DEMO_INVITE_CODE = "VIVO-01";
    private static final String LEGACY_DEMO_INVITE_CODE = "CHANGNIAN-DEMO";

    private final FamilyMapper familyMapper;
    private final UserMapper userMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final DailyStatusMapper dailyStatusMapper;
    private final TopicCardMapper topicCardMapper;
    private final MedicationScheduleMapper medicationScheduleMapper;
    private final MedicationRecordMapper medicationRecordMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final ElderResponseMapper elderResponseMapper;
    private final FamilyMemoryMapper familyMemoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final boolean seedDemoHistory;

    public DemoDataInitializer(
            FamilyMapper familyMapper,
            UserMapper userMapper,
            WeeklyReportMapper weeklyReportMapper,
            DailyStatusMapper dailyStatusMapper,
            TopicCardMapper topicCardMapper,
            MedicationScheduleMapper medicationScheduleMapper,
            MedicationRecordMapper medicationRecordMapper,
            MedicalRecordMapper medicalRecordMapper,
            ElderResponseMapper elderResponseMapper,
            FamilyMemoryMapper familyMemoryMapper,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            @Value("${app.demo.seed-history:false}") boolean seedDemoHistory
    ) {
        this.familyMapper = familyMapper;
        this.userMapper = userMapper;
        this.weeklyReportMapper = weeklyReportMapper;
        this.dailyStatusMapper = dailyStatusMapper;
        this.topicCardMapper = topicCardMapper;
        this.medicationScheduleMapper = medicationScheduleMapper;
        this.medicationRecordMapper = medicationRecordMapper;
        this.medicalRecordMapper = medicalRecordMapper;
        this.elderResponseMapper = elderResponseMapper;
        this.familyMemoryMapper = familyMemoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.seedDemoHistory = seedDemoHistory;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUserGenderColumn();
        ensureElderScopeColumns();
        FamilyEntity family = ensureFamily();
        UserEntity child = ensureDemoUser(family.getId(), "Child", "child", "本人", "CHILD", "", "", "16888");
        UserEntity father = ensureDemoUser(family.getId(), "elder_demo", "father", "爸爸", "ELDER", "", "男", "16888");
        UserEntity mother = ensureUser(family.getId(), "mother", "妈妈", "ELDER", "", "女", "16888");
        normalizeDemoMedicationSchedules(family.getId(), father.getId(), mother.getId());
        if (!seedDemoHistory) {
            return;
        }
        ensureWeeklyReport(family.getId(), father.getId(), "爸爸");
        ensureWeeklyReport(family.getId(), mother.getId(), "妈妈");
        ensureDailyStatuses(family.getId(), father.getId(), "爸爸");
        ensureDailyStatuses(family.getId(), mother.getId(), "妈妈");
        ensureTopicCards(family.getId(), father.getId(), mother.getId());
        ensureMedicationRecords(family.getId(), father.getId(), mother.getId());
        ensureMedicalRecords(family.getId(), father.getId(), mother.getId());
        ensureResponsesAndMemories(family.getId(), father.getId(), mother.getId());
    }

    private FamilyEntity ensureFamily() {
        FamilyEntity family = familyMapper.selectOne(Wrappers.<FamilyEntity>lambdaQuery()
                .eq(FamilyEntity::getInviteCode, DEMO_INVITE_CODE));
        if (family != null) {
            return family;
        }
        family = familyMapper.selectOne(Wrappers.<FamilyEntity>lambdaQuery()
                .eq(FamilyEntity::getInviteCode, LEGACY_DEMO_INVITE_CODE));
        if (family != null) {
            family.setInviteCode(DEMO_INVITE_CODE);
            familyMapper.updateById(family);
            return family;
        }
        family = new FamilyEntity();
        family.setName("林晓的家");
        family.setInviteCode(DEMO_INVITE_CODE);
        family.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(family);
        return family;
    }

    private void ensureUserGenderColumn() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND COLUMN_NAME = 'gender'
                """, Long.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN gender VARCHAR(20)");
    }

    private void ensureElderScopeColumns() {
        ensureColumn("weekly_reports", "elder_id", "ALTER TABLE weekly_reports ADD COLUMN elder_id BIGINT NULL AFTER family_id");
        ensureColumn("daily_statuses", "elder_id", "ALTER TABLE daily_statuses ADD COLUMN elder_id BIGINT NULL AFTER family_id");
        ensureColumn("topic_cards", "elder_id", "ALTER TABLE topic_cards ADD COLUMN elder_id BIGINT NULL AFTER family_id");
        ensureIndex("weekly_reports", "idx_weekly_elder_created", "ALTER TABLE weekly_reports ADD INDEX idx_weekly_elder_created (elder_id, created_at)");
        ensureIndex("daily_statuses", "idx_daily_family_date", "ALTER TABLE daily_statuses ADD INDEX idx_daily_family_date (family_id, status_date)");
        ensureIndex("topic_cards", "idx_topic_elder_created", "ALTER TABLE topic_cards ADD INDEX idx_topic_elder_created (elder_id, created_at)");
        replaceDailyStatusUniqueIndex();
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Long.class, tableName, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private void ensureIndex(String tableName, String indexName, String alterSql) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Long.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private void replaceDailyStatusUniqueIndex() {
        Long oldIndex = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'daily_statuses'
                  AND INDEX_NAME = 'uk_daily_family_date'
                """, Long.class);
        if (oldIndex != null && oldIndex > 0) {
            jdbcTemplate.execute("ALTER TABLE daily_statuses DROP INDEX uk_daily_family_date");
        }
        ensureIndex("daily_statuses", "uk_daily_family_elder_date",
                "ALTER TABLE daily_statuses ADD UNIQUE KEY uk_daily_family_elder_date (family_id, elder_id, status_date)");
    }

    private UserEntity ensureDemoUser(Long familyId, String legacyUsername, String username, String displayName, String role, String phone, String gender, String password) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username));
        if (user == null) {
            user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                    .eq(UserEntity::getUsername, legacyUsername));
            if (user != null) {
                user.setUsername(username);
            }
        }
        return saveUser(user, familyId, username, displayName, role, phone, gender, password);
    }

    private UserEntity ensureUser(Long familyId, String username, String displayName, String role, String phone, String gender, String password) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username));
        return saveUser(user, familyId, username, displayName, role, phone, gender, password);
    }

    private UserEntity saveUser(UserEntity user, Long familyId, String username, String displayName, String role, String phone, String gender, String password) {
        boolean exists = user != null;
        if (!exists) {
            user = new UserEntity();
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setFamilyId(familyId);
        user.setUsername(username);
        if (passwordNeedsUpdate(password, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        user.setDisplayName(textOrDefault(user.getDisplayName(), displayName));
        user.setRole(role);
        user.setPhone(textOrDefault(user.getPhone(), phone));
        user.setGender(textOrDefault(user.getGender(), gender));
        if (exists) {
            userMapper.updateById(user);
        } else {
            userMapper.insert(user);
        }
        return user;
    }

    private boolean passwordNeedsUpdate(String password, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return true;
        }
        try {
            return !passwordEncoder.matches(password, passwordHash);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private String textOrDefault(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "" : fallback;
    }

    private void ensureWeeklyReport(Long familyId, Long elderId, String elderName) {
        Long count = weeklyReportMapper.selectCount(Wrappers.<WeeklyReportEntity>lambdaQuery()
                .eq(WeeklyReportEntity::getFamilyId, familyId)
                .eq(WeeklyReportEntity::getElderId, elderId));
        if (count > 0) {
            return;
        }
        WeeklyReportEntity report = new WeeklyReportEntity();
        report.setFamilyId(familyId);
        report.setElderId(elderId);
        report.setWeekLabel(LocalDate.now().getYear() + "年" + LocalDate.now().getMonthValue() + "月第" + (((LocalDate.now().getDayOfMonth() - 1) / 7) + 1) + "周");
        if ("妈妈".equals(elderName)) {
            report.setTitle("妈妈这周惦记家里小事");
            report.setSummary("这周妈妈的状态整体平稳。她聊到天气热、晚饭准备蒸包子，也提醒蓝心下班路上别着急，记得带水。用药记录显示钙片和维生素都按时确认，周末她还提到想把小外孙的新照片打印出来放在床头。");
            report.setKeywords("晚饭,天气,小外孙,用药");
            report.setQuoteText("她说：今天外头热，你下班路上别急，记得喝水。");
        } else {
            report.setTitle("爸爸这周过得挺稳当");
            report.setSummary("这周爸爸的生活节奏比较规律。早上出门买菜，傍晚在小区里散步，周三还碰见李叔叔下了一盘棋。用药记录显示维生素C和鱼油都有确认，爸爸提到最近睡得还可以，只是天气闷的时候不太想出门。");
            report.setKeywords("散步,买菜,下棋,睡眠");
            report.setQuoteText("他说：没啥大事，就是天热了少走两圈。");
        }
        report.setCreatedAt(LocalDateTime.now());
        weeklyReportMapper.insert(report);
    }

    private void ensureDailyStatuses(Long familyId, Long elderId, String elderName) {
        Long count = dailyStatusMapper.selectCount(Wrappers.<DailyStatusEntity>lambdaQuery()
                .eq(DailyStatusEntity::getFamilyId, familyId)
                .eq(DailyStatusEntity::getElderId, elderId));
        if (count > 0) {
            return;
        }
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate today = LocalDate.now();
        String[] motherContents = {
                "妈妈早上去楼下买了青菜和豆腐，说天气热，午饭想做得清淡一点。",
                "妈妈中午和邻居王阿姨聊了几句，提到小外孙照片看着很精神。",
                "妈妈下午把床单晒了，说太阳好，晚上睡觉会舒服些。",
                "妈妈今天上午剁了白菜猪肉馅，准备晚上蒸包子，还提醒蓝心下班路上别忘了喝水。",
                "妈妈说晚饭后在小区慢慢走了一圈，回来按时吃了钙片。",
                "妈妈今天情绪挺轻松，问周末忙不忙，还说家里这边都挺好。"
        };
        String[] fatherContents = {
                "爸爸早上七点多起床，去菜市场买了黄瓜和鸡蛋，回来后精神不错。",
                "爸爸上午整理了阳台花盆，说有两盆绿萝该换土了。",
                "爸爸傍晚下楼散步二十分钟，回来后说天气闷，明天早点出门。",
                "爸爸周三碰见李叔叔，下了一盘棋，回家路上买了两个橘子。",
                "爸爸晚饭后确认了鱼油，说最近睡得还可以，只是午后容易犯困。",
                "爸爸今天在家看了会新闻，下午给老同事回了电话，语气挺轻松。"
        };
        String[] contents = "妈妈".equals(elderName) ? motherContents : fatherContents;
        LocalDate cursor = start;
        int index = 0;
        while (!cursor.isAfter(today)) {
            String title = cursor.equals(today) ? "今天" : cursor.getMonthValue() + "/" + cursor.getDayOfMonth();
            String content = contents[Math.min(index, contents.length - 1)];
            String tone = content.contains("闷") || content.contains("犯困") ? "watch" : (index % 3 == 1 ? "good" : "normal");
            insertDailyAt(familyId, elderId, cursor, title, content, tone);
            cursor = cursor.plusDays(1);
            index++;
        }
    }

    private void insertDailyAt(Long familyId, Long elderId, LocalDate date, String title, String content, String tone) {
        DailyStatusEntity status = new DailyStatusEntity();
        status.setFamilyId(familyId);
        status.setElderId(elderId);
        status.setStatusDate(date);
        status.setTitle(title);
        status.setContent(content);
        status.setTone(tone);
        dailyStatusMapper.insert(status);
    }

    private void ensureTopicCards(Long familyId, Long fatherId, Long motherId) {
        Long fatherCount = topicCardMapper.selectCount(Wrappers.<TopicCardEntity>lambdaQuery()
                .eq(TopicCardEntity::getFamilyId, familyId)
                .eq(TopicCardEntity::getElderId, fatherId)
                .eq(TopicCardEntity::getStatus, "ACTIVE"));
        if (fatherCount == 0) {
            insertTopic(familyId, fatherId, "日常唠嗑", "接着聊那盘棋", "爸爸周三和李叔叔下棋赢了，可以问问他那盘棋怎么赢的。", "周三傍晚在小区下棋。");
            insertTopic(familyId, fatherId, "健康关心", "问问睡得怎么样", "爸爸这两天说午后容易犯困，可以轻轻问问晚上睡得踏不踏实。", "最近提到午后犯困。");
            insertTopic(familyId, fatherId, "生活习惯", "阳台花还要不要换土", "爸爸提到阳台绿萝该换土了，可以顺着问问哪盆长得最好。", "上午整理了阳台花盆。");
            insertTopic(familyId, fatherId, "关系人询问", "李叔叔最近怎么样", "爸爸最近提起李叔叔，可以问问他们还常不常一起下棋。", "最近提到李叔叔。");
            insertTopic(familyId, fatherId, "日常提醒", "天气闷就少走两圈", "天气热的时候可以先顺着他说，不催他多运动，只提醒慢慢来。", "天气闷时不太想出门。");
        }
        Long motherCount = topicCardMapper.selectCount(Wrappers.<TopicCardEntity>lambdaQuery()
                .eq(TopicCardEntity::getFamilyId, familyId)
                .eq(TopicCardEntity::getElderId, motherId)
                .eq(TopicCardEntity::getStatus, "ACTIVE"));
        if (motherCount == 0) {
            insertTopic(familyId, motherId, "日常唠嗑", "问问包子蒸得怎么样", "妈妈说准备蒸白菜猪肉包子，可以问问馅儿调得香不香。", "今天上午剁了白菜猪肉馅。");
            insertTopic(familyId, motherId, "关系陪伴", "小外孙照片你看了吗", "妈妈提到小外孙照片，可以问问她想不想再看几张新的。", "提到小外孙照片。");
            insertTopic(familyId, motherId, "健康关心", "钙片记得吃了吗", "妈妈最近按时确认钙片，可以自然问一句今天吃过没有，别问得太严肃。", "钙片和维生素按时确认。");
            insertTopic(familyId, motherId, "邻里小事", "王阿姨最近忙什么", "妈妈和王阿姨聊过几句，可以顺着问问楼下最近有没有新鲜事。", "中午和邻居王阿姨聊天。");
            insertTopic(familyId, motherId, "天气关心", "天热就少晒一会儿", "妈妈晒了床单，可以提醒她太阳大时别在外面站太久。", "下午晒了床单。");
        }
    }

    private void insertTopic(Long familyId, Long elderId, String category, String title, String content, String sourceText) {
        TopicCardEntity topic = new TopicCardEntity();
        topic.setFamilyId(familyId);
        topic.setElderId(elderId);
        topic.setCategory(category);
        topic.setTitle(title);
        topic.setContent(content);
        topic.setSourceText(sourceText);
        topic.setStatus("ACTIVE");
        topic.setCreatedAt(LocalDateTime.now());
        topicCardMapper.insert(topic);
    }

    private void normalizeDemoMedicationSchedules(Long familyId, Long fatherId, Long motherId) {
        medicationRecordMapper.delete(Wrappers.<MedicationRecordEntity>lambdaQuery()
                .eq(MedicationRecordEntity::getFamilyId, familyId)
                .in(MedicationRecordEntity::getElderId, fatherId, motherId));
        medicationScheduleMapper.delete(Wrappers.<MedicationScheduleEntity>lambdaQuery()
                .eq(MedicationScheduleEntity::getFamilyId, familyId)
                .in(MedicationScheduleEntity::getElderId, fatherId, motherId));

        insertMedication(familyId, motherId, "钙片", "1 片", LocalTime.of(8, 0), "早饭后吃");
        insertMedication(familyId, motherId, "维生素D软胶囊", "1 粒", LocalTime.of(12, 30), "午饭后吃");
        insertMedication(familyId, motherId, "复合维生素片", "1 片", LocalTime.of(20, 0), "晚饭后吃");

        insertMedication(familyId, fatherId, "维生素C片", "1 片", LocalTime.of(8, 30), "早饭后吃");
        insertMedication(familyId, fatherId, "鱼油软胶囊", "1 粒", LocalTime.of(19, 30), "晚饭后吃");
    }

    private void insertMedication(Long familyId, Long elderId, String name, String dosage, LocalTime time, String note) {
        MedicationScheduleEntity schedule = new MedicationScheduleEntity();
        schedule.setFamilyId(familyId);
        schedule.setElderId(elderId);
        schedule.setMedicineName(name);
        schedule.setDosage(dosage);
        schedule.setRemindTime(time);
        schedule.setNote(note);
        schedule.setEnabled(true);
        schedule.setCreatedAt(LocalDateTime.now());
        medicationScheduleMapper.insert(schedule);
    }

    private void ensureMedicationRecords(Long familyId, Long fatherId, Long motherId) {
        seedMedicationRecordsForElder(familyId, motherId);
        seedMedicationRecordsForElder(familyId, fatherId);
    }

    private void seedMedicationRecordsForElder(Long familyId, Long elderId) {
        List<MedicationScheduleEntity> schedules = medicationScheduleMapper.selectList(Wrappers.<MedicationScheduleEntity>lambdaQuery()
                .eq(MedicationScheduleEntity::getFamilyId, familyId)
                .eq(MedicationScheduleEntity::getElderId, elderId)
                .eq(MedicationScheduleEntity::getEnabled, true)
                .orderByAsc(MedicationScheduleEntity::getRemindTime));
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate today = LocalDate.now();
        LocalDate cursor = start;
        while (!cursor.isAfter(today)) {
            for (MedicationScheduleEntity schedule : schedules) {
                if (cursor.equals(today) && schedule.getRemindTime().isAfter(LocalTime.now())) {
                    continue;
                }
                insertMedicationRecord(familyId, elderId, schedule.getId(), "TAKEN", cursor, cursor.atTime(schedule.getRemindTime()).plusMinutes(8));
            }
            cursor = cursor.plusDays(1);
        }
    }

    private void insertMedicationRecord(Long familyId, Long elderId, Long scheduleId, String status, LocalDate recordDate, LocalDateTime actionTime) {
        MedicationRecordEntity record = new MedicationRecordEntity();
        record.setFamilyId(familyId);
        record.setElderId(elderId);
        record.setScheduleId(scheduleId);
        record.setStatus(status);
        record.setRecordDate(recordDate);
        record.setActionTime(actionTime);
        medicationRecordMapper.insert(record);
    }

    private void ensureMedicalRecords(Long familyId, Long fatherId, Long motherId) {
        seedMedicalRecordsForElder(familyId, motherId, List.of(
                new MedicalSeed("体检记录", "社区体检：血压、血糖基础检查", -5),
                new MedicalSeed("购药记录", "药店购买钙片和维生素D", -3),
                new MedicalSeed("复查提醒", "下周三上午社区门诊复查", 9)
        ));
        seedMedicalRecordsForElder(familyId, fatherId, List.of(
                new MedicalSeed("体检记录", "社区体检：基础血压和心率检查", -4),
                new MedicalSeed("购药记录", "药店购买维生素C片和鱼油软胶囊", -2),
                new MedicalSeed("复查提醒", "下周五上午社区门诊复查", 11)
        ));
    }

    private void seedMedicalRecordsForElder(Long familyId, Long elderId, List<MedicalSeed> seeds) {
        Long count = medicalRecordMapper.selectCount(Wrappers.<MedicalRecordEntity>lambdaQuery()
                .eq(MedicalRecordEntity::getFamilyId, familyId)
                .eq(MedicalRecordEntity::getElderId, elderId));
        if (count > 0) {
            return;
        }
        for (MedicalSeed seed : seeds) {
            MedicalRecordEntity record = new MedicalRecordEntity();
            record.setFamilyId(familyId);
            record.setElderId(elderId);
            record.setRecordType(seed.type());
            record.setTitle(seed.title());
            record.setFileUrl("");
            record.setCreatedAt(LocalDate.now().plusDays(seed.daysFromToday()).atTime(9, 30));
            medicalRecordMapper.insert(record);
        }
    }

    private void ensureResponsesAndMemories(Long familyId, Long fatherId, Long motherId) {
        seedResponsesAndMemoriesForElder(familyId, motherId, "妈妈", List.of(
                new MemorySeed("主动留言", "今天外头热，你下班路上别急，记得喝水。我这边都挺好，别老惦记。", "天气叮嘱", "妈妈提醒蓝心下班路上别急，记得喝水。", -1, 18),
                new MemorySeed("主动留言", "我上午剁了白菜猪肉馅，晚上蒸包子。你周末要是不忙，回来拿几个。", "准备蒸包子", "妈妈说上午剁了白菜猪肉馅，晚上准备蒸包子。", -2, 10),
                new MemorySeed("主动留言", "小外孙那张照片我看了，笑得真好，我想明天去打印一张。", "想打印照片", "妈妈说看了小外孙照片，想打印一张放在家里。", -3, 15)
        ));
        seedResponsesAndMemoriesForElder(familyId, fatherId, "爸爸", List.of(
                new MemorySeed("主动留言", "今天碰见李叔叔，下了一盘棋，走回来有点热，没多待。", "碰见李叔叔", "爸爸说今天碰见李叔叔，下了一盘棋后就回家了。", -1, 19),
                new MemorySeed("主动留言", "阳台那两盆绿萝长得还行，我准备明天换点新土。", "阳台绿萝", "爸爸说阳台两盆绿萝长得还行，准备换点新土。", -2, 16),
                new MemorySeed("主动留言", "鱼油我吃过了，晚上准备早点睡，明早去买菜。", "早点休息", "爸爸说鱼油已经吃过，晚上准备早点休息。", -3, 20)
        ));
    }

    private void seedResponsesAndMemoriesForElder(Long familyId, Long elderId, String elderName, List<MemorySeed> seeds) {
        Long count = familyMemoryMapper.selectCount(Wrappers.<FamilyMemoryEntity>lambdaQuery()
                .eq(FamilyMemoryEntity::getFamilyId, familyId)
                .eq(FamilyMemoryEntity::getElderId, elderId));
        if (count > 0) {
            return;
        }
        for (MemorySeed seed : seeds) {
            ElderResponseEntity response = new ElderResponseEntity();
            response.setFamilyId(familyId);
            response.setElderId(elderId);
            response.setRequestId(null);
            response.setSourceType(seed.sourceType());
            response.setContent(seed.responseContent());
            response.setAudioUrl("");
            LocalDateTime createdAt = LocalDateTime.now().plusDays(seed.daysFromToday()).withHour(seed.hour()).withMinute(6).withSecond(0).withNano(0);
            response.setCreatedAt(createdAt);
            elderResponseMapper.insert(response);

            FamilyMemoryEntity memory = new FamilyMemoryEntity();
            memory.setFamilyId(familyId);
            memory.setElderId(elderId);
            memory.setResponseId(response.getId());
            memory.setTitle(seed.memoryTitle());
            memory.setContent(seed.memoryContent().startsWith(elderName) ? seed.memoryContent() : elderName + "说：" + seed.memoryContent());
            memory.setSourceType("AI");
            memory.setCreatedAt(createdAt.plusMinutes(1));
            familyMemoryMapper.insert(memory);
        }
    }

    private record MedicalSeed(String type, String title, int daysFromToday) {
    }

    private record MemorySeed(String sourceType, String responseContent, String memoryTitle, String memoryContent, int daysFromToday, int hour) {
    }
}
