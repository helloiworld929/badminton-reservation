package com.badminton.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Court {
    private Long id;
    private String name;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
