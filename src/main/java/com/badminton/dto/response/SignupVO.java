package com.badminton.dto.response;

import lombok.Data;

@Data
public class SignupVO {
    private Long id;
    private Long activityId;
    private Long userId;
    private String name;
    private String phone;
    private String nickname;
    private String avatar;
}
