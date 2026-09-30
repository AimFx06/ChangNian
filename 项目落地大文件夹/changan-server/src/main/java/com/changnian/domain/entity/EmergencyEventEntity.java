package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emergency_events")
public class EmergencyEventEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private String status;
    private String locationText;
    private String note;
    private LocalDateTime createdAt;
}
