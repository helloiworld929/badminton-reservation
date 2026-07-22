package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
public class ActivitySignupRequest {
    @NotNull(message = "活动ID不能为空")
    private Long activityId;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;
}
