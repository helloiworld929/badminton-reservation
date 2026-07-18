package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CreateReservationRequest {
    @NotNull(message = "场地ID不能为空")
    private Long courtId;
    @NotNull(message = "预约日期不能为空")
    private String date;
    @NotNull(message = "开始时间不能为空")
    private Integer startTime;
    @NotNull(message = "结束时间不能为空")
    private Integer endTime;
}
