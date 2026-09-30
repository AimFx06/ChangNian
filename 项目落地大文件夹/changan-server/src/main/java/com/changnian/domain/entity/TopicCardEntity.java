package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("topic_cards")
public class TopicCardEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private String category;
    private String title;
    private String content;
    private String sourceText;
    private String status;
    private LocalDateTime createdAt;
}
