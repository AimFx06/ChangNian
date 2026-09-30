package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("microphone_requests")
public class MicrophoneRequestEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long topicId;
    private Long childId;
    private Long elderId;
    private String promptText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
