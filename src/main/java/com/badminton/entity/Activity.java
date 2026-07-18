package com.badminton.entity;

import lombok.Data;

@Data
public class Activity {
    private Long id;
    private String title;
    private String activityTime;
    private String location;
    private String description;
    private String imageUrl;

    private transient Integer signupCount;
}
