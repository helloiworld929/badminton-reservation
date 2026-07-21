package com.badminton.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ForumPostImage {
    private Long id;
    private Long postId;
    private String imageUrl;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
}
