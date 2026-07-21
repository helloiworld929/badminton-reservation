package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ForumStatusRequest {
    @NotBlank(message = "状态不能为空")
    private String status;
}
