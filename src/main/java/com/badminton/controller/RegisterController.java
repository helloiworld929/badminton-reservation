package com.badminton.controller;

import com.badminton.common.ApiResponse;
import com.badminton.common.BusinessException;
import com.badminton.entity.User;
import com.badminton.mapper.UserMapper;
import com.badminton.service.OssService;
import com.badminton.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/register")
public class RegisterController {
    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    private final SmsService smsService;
    private final OssService ossService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public RegisterController(SmsService smsService, OssService ossService, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.smsService = smsService;
        this.ossService = ossService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 向指定手机号发送短信验证码。
     */
    @PostMapping("/send-code")
    public ApiResponse<Map<String, Object>> sendCode(@Valid @RequestBody SendCodeRequest request) {
        // 先查重再发短信
        User existing = userMapper.findByPhone(request.getPhone());
        if (existing != null) {
            throw new BusinessException("该手机号已注册");
        }
        smsService.sendCode(request.getPhone());
        Map<String, Object> data = new HashMap<>();
        data.put("phone", request.getPhone());
        data.put("expireSeconds", 300);
        return ApiResponse.success(data);
    }

    /**
     * 使用手机号 + 验证码 + 密码完成注册。
     */
    @PostMapping
    public ApiResponse<User> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        // 1. Validate verification code
        smsService.validateCode(request.getPhone(), request.getCode());

        // 2. Check if phone already registered
        User existingByPhone = userMapper.findByPhone(request.getPhone());
        if (existingByPhone != null) {
            throw new BusinessException("该手机号已注册");
        }

        // 3. Create user
        User user = new User();
        user.setNickname(request.getNickname().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setGender(request.getGender() != null && !request.getGender().isBlank()
                ? request.getGender() : "男");
        user.setAge(request.getAge());
        user.setStatus("active");
        user.setRole("user");
        user.setNoshowCount(0);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // 注册后自动登录
        session.setAttribute("userId", user.getId());
        session.setAttribute("role", user.getRole());

        log.info("新用户注册: id={}", user.getId());
        return ApiResponse.success(user);
    }

    /**
     * 上传头像图片到 OSS，返回公开访问 URL。
     */
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

    // ==== 请求 DTO ====

    @lombok.Data
    public static class SendCodeRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
        private String phone;
    }

    @lombok.Data
    public static class RegisterRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
        private String phone;

        @NotBlank(message = "验证码不能为空")
        private String code;

        @NotBlank(message = "密码不能为空")
        private String password;

        @NotBlank(message = "昵称不能为空")
        private String nickname;

        @NotNull(message = "年龄不能为空")
        @Min(value = 6, message = "年龄必须在6到60之间")
        @Max(value = 60, message = "年龄必须在6到60之间")
        private Integer age;

        private String avatar;
        private String gender;
    }
}
