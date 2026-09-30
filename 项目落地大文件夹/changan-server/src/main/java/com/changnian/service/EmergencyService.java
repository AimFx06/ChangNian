package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.EmergencyEventEntity;
import com.changnian.domain.mapper.EmergencyEventMapper;
import com.changnian.websocket.SocketHub;
import com.changnian.websocket.SocketMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class EmergencyService {
    private final EmergencyEventMapper emergencyEventMapper;
    private final SocketHub socketHub;

    public EmergencyService(EmergencyEventMapper emergencyEventMapper, SocketHub socketHub) {
        this.emergencyEventMapper = emergencyEventMapper;
        this.socketHub = socketHub;
    }

    public EmergencyEventEntity create(String locationText, String note) {
        CurrentUser user = UserContext.get();
        if (user.role() != Role.ELDER) {
            throw new BizException("只有老人端可以触发 SOS");
        }
        EmergencyEventEntity event = new EmergencyEventEntity();
        event.setFamilyId(user.familyId());
        event.setElderId(user.id());
        event.setStatus("ACTIVE");
        event.setLocationText(locationText);
        event.setNote(note);
        event.setCreatedAt(LocalDateTime.now());
        emergencyEventMapper.insert(event);
        socketHub.sendToFamilyRole(user.familyId(), Role.CHILD, new SocketMessage("EMERGENCY_CREATED", event));
        return event;
    }

    public EmergencyEventEntity confirm(Long id) {
        CurrentUser user = UserContext.get();
        if (user.role() != Role.CHILD) {
            throw new BizException("只有子女端可以确认 SOS");
        }
        EmergencyEventEntity event = emergencyEventMapper.selectById(id);
        if (event == null || !event.getFamilyId().equals(user.familyId())) {
            throw new BizException("SOS 记录不存在");
        }
        emergencyEventMapper.update(null, Wrappers.<EmergencyEventEntity>lambdaUpdate()
                .eq(EmergencyEventEntity::getFamilyId, user.familyId())
                .eq(EmergencyEventEntity::getStatus, "ACTIVE")
                .set(EmergencyEventEntity::getStatus, "CONFIRMED"));
        event.setStatus("CONFIRMED");
        return event;
    }
}
