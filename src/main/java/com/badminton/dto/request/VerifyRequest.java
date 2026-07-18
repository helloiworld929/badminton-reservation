package com.badminton.dto.request;

import lombok.Data;

@Data
public class VerifyRequest {
    private String code;
    private Long reservationId;
    private String scanContent;
}
