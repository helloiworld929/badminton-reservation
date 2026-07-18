package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UpdateCourtStatusRequest {
    @NotNull(message = "状态不能为空")
    private Integer status;
    private String remark;
}
