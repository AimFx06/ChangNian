package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("medication_schedules")
public class MedicationScheduleEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private String medicineName;
    private String dosage;
    private LocalTime remindTime;
    private String note;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
