package com.badminton.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ForumPost {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private String status;
    private Boolean pinned;
    private Integer viewCount;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 列表与详情查询的关联展示字段
    private transient String authorNickname;
    private transient String authorAvatar;
    private transient Integer replyCount;
    private transient List<String> imageUrls = new ArrayList<>();
}
