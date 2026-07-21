package com.badminton.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForumReply {
    private Long id;
    private Long postId;
    private Long userId;
    private String content;
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;

    private transient String authorNickname;
    private transient String authorAvatar;
}
