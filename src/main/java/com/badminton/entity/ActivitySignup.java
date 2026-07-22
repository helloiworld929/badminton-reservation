package com.badminton.entity;

import lombok.Data;

@Data
public class ActivitySignup {
    private Long id;
    private Long activityId;
    private Long userId;
    private String name;
    private String phone;
}
