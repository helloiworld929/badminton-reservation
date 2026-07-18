package com.badminton.controller;

import com.badminton.common.ApiResponse;
import com.badminton.common.BusinessException;
import com.badminton.dto.request.UpdateUserRequest;
import com.badminton.entity.User;
import com.badminton.service.OssService;
import com.badminton.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// 用户个人中心控制器：获取/修改个人信息、绑定手机号、上传头像
@RestController
@RequestMapping
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final OssService ossService;

    public UserController(UserService userService, OssService ossService) {
        this.userService = userService;
        this.ossService = ossService;
    }

    @GetMapping("/user")
    public ApiResponse<User> getUser(HttpSession session) {
        return ApiResponse.success(userService.getUser(getUserId(session)));
    }

    @PostMapping("/user")
    public ApiResponse<User> updateUser(@Valid @RequestBody UpdateUserRequest request, HttpSession session) {
        return ApiResponse.success(userService.updateUser(getUserId(session), request));
    }

    // 发送短信验证码用于绑定手机号，校验手机号格式
    @PostMapping("/user/send-phone-code")
    public ApiResponse<Map<String, Object>> sendPhoneCode(@RequestBody Map<String, String> body, HttpSession session) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("1[3-9]\\d{9}")) {
            throw new BusinessException("手机号格式不正确");
        }
        userService.sendPhoneCode(getUserId(session), phone);
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("expireSeconds", 300);
        return ApiResponse.success(data);
    }

    // 上传用户头像至OSS，返回可访问URL
    @PostMapping("/upload-avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            String url = ossService.upload(file, "avatar");
            Map<String, String> data = new HashMap<>();
            data.put("url", url);
            return ApiResponse.success(data);
        } catch (IOException e) {
            log.error("头像上传失败", e);
            throw new BusinessException("头像上传失败: " + e.getMessage());
        }
    }

    private long getUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number) return ((Number) userId).longValue();
        throw new IllegalArgumentException("请先登录");
    }
}
