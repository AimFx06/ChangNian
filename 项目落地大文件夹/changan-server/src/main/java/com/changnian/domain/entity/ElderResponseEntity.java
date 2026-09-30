package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("elder_responses")
public class ElderResponseEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long requestId;
    private Long elderId;
    private String sourceType;
    private String content;
    private String audioUrl;
    private LocalDateTime createdAt;
}
