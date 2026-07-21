package com.badminton.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForumReport {
    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String status;
    private String result;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;

    private transient String reporterNickname;
}
