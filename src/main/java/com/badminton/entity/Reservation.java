package com.badminton.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class Reservation {
    private Long id;
    private Long userId;
    private Long courtId;
    private LocalDate reserveDate;
    private Integer startTime;
    private Integer endTime;
    private String status;
    private String verificationCode;
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
    private LocalDateTime createdAt;

    // JOIN 字段（非数据库字段）
    private transient String courtName;
    private transient String userAvatar;
    private transient String userGender;
    private transient String userNickname;
    private transient Integer userAge;
    private transient String userPhone;
}
