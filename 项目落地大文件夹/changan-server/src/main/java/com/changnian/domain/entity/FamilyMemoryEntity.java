package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("family_memories")
public class FamilyMemoryEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private Long responseId;
    private String title;
    private String content;
    private String sourceType;
    private LocalDateTime createdAt;
}
