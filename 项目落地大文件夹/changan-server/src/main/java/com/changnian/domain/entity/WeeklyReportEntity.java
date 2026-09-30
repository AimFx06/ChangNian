package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("weekly_reports")
public class WeeklyReportEntity {
    @TableId
    private Long id;
    private Long familyId;
    private Long elderId;
    private String weekLabel;
    private String title;
    private String summary;
    private String keywords;
    private String quoteText;
    private LocalDateTime createdAt;
}
