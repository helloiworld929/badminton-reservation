package com.badminton.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Data
public class ForumPostCreateRequest {
    @NotBlank(message = "帖子标题不能为空")
    @Size(min = 5, max = 80, message = "帖子标题长度必须为5到80个字符")
    private String title;

    @NotBlank(message = "帖子正文不能为空")
    @Size(max = 2000, message = "帖子正文不能超过2000个字符")
    private String content;

    @NotBlank(message = "请选择帖子分类")
    private String category;

    @Size(max = 3, message = "每个帖子最多上传3张图片")
    private List<MultipartFile> images = new ArrayList<>();
}
