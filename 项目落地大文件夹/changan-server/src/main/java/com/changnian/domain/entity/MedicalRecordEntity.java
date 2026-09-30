package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_records")
public class MedicalRecordEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private String recordType;
    private String title;
    private String fileUrl;
    private LocalDateTime createdAt;
}
