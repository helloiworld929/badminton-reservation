package com.badminton.dto.response;

import com.badminton.entity.OperationLog;
import lombok.Data;

import java.util.List;

@Data
public class ReservationVO {
    private Long id;
    private Long userId;
    private Long courtId;
    private String courtName;
    private String reserveDate;
    private Integer startTime;
    private Integer endTime;
    private String status;
    private String statusDisplay;
    private String verificationCode;
    private String userAvatar;
    private String userGender;
    private String userNickname;
    private String userPhone;
    private String createdAt;
    private String verifiedAt;
    private List<OperationLog> timeline;
}
