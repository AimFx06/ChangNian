package com.changnian.service;

import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.MedicationRecordEntity;
import com.changnian.domain.entity.MedicationScheduleEntity;
import com.changnian.domain.mapper.MedicationRecordMapper;
import com.changnian.domain.mapper.MedicationScheduleMapper;
import com.changnian.websocket.SocketHub;
import com.changnian.websocket.SocketMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class MedicationService {
    private final MedicationScheduleMapper scheduleMapper;
    private final MedicationRecordMapper recordMapper;
    private final SocketHub socketHub;

    public MedicationService(MedicationScheduleMapper scheduleMapper, MedicationRecordMapper recordMapper, SocketHub socketHub) {
        this.scheduleMapper = scheduleMapper;
        this.recordMapper = recordMapper;
        this.socketHub = socketHub;
    }

    public MedicationRecordEntity record(Long scheduleId, String status) {
        CurrentUser user = UserContext.get();
        if (user.role() != Role.ELDER) {
            throw new BizException("只有老人端可以确认用药");
        }
        MedicationScheduleEntity schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || !schedule.getFamilyId().equals(user.familyId()) || !schedule.getElderId().equals(user.id())) {
            throw new BizException("用药提醒不存在");
        }
        MedicationRecordEntity record = new MedicationRecordEntity();
        record.setFamilyId(user.familyId());
        record.setScheduleId(scheduleId);
        record.setElderId(user.id());
        record.setStatus(status);
        record.setRecordDate(LocalDate.now());
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);
        socketHub.sendToFamilyRole(user.familyId(), Role.CHILD, new SocketMessage("MEDICATION_RECORD_CREATED", record));
        return record;
    }
}
