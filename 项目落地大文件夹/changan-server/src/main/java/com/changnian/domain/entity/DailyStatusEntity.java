package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("daily_statuses")
public class DailyStatusEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private LocalDate statusDate;
    private String title;
    private String content;
    private String tone;
}
