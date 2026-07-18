package com.badminton.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CreateCourtRequest {
    @NotBlank(message = "场地名称不能为空")
    private String name;
    private String remark;
}
