package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ForumReportHandleRequest {
    @NotBlank(message = "处理状态不能为空")
    private String status;
    @Size(max = 200, message = "处理说明不能超过200个字符")
    private String result;
}
