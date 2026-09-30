package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("families")
public class FamilyEntity {
    @TableId
    private Long id;
    private String name;
    private String inviteCode;
    private LocalDateTime createdAt;
}
