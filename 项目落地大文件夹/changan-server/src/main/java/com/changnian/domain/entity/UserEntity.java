package com.changnian.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class UserEntity {
    @TableId
    private Long id;
    private Long familyId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String role;
    private String phone;
    private String gender;
    private LocalDateTime createdAt;
}
