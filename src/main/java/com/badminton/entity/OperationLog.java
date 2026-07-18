package com.badminton.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationLog {
    private Long id;
    private Long reservationId;
    private Long userId;
    private Long operatorId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
