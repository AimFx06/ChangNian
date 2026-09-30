package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("medication_records")
public class MedicationRecordEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long scheduleId;
    private Long elderId;
    private String status;
    private LocalDate recordDate;
    private LocalDateTime actionTime;
}
