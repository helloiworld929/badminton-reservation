package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ForumReportRequest {
    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;
    @NotNull(message = "举报对象不能为空")
    private Long targetId;
    @NotBlank(message = "请选择举报原因")
    private String reason;
}
