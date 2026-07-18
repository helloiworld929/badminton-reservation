package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

@Data
public class UpdateUserRequest {
    private String nickname;
    private String gender;
    @Min(value = 0, message = "年龄必须在0到100之间")
    @Max(value = 100, message = "年龄必须在0到100之间")
    private Integer age;
    private String username;
    @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;
    private String avatar;
    private String code;
    private String newPassword;
}
