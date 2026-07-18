package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.dto.request.LoginRequest;
import com.badminton.dto.request.UpdateUserRequest;
import com.badminton.entity.User;
import com.badminton.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// 用户业务：登录认证、个人信息修改、手机号验证、状态管理
@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, SmsService smsService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
    }

    // 账号支持用户名或手机号登录，统一先用用户名查，查不到再按手机号查
    public User login(LoginRequest request) {
        String account = request.getUsername().trim();
        User user = userMapper.findByUsername(account);
        if (user == null) {
            user = userMapper.findByPhone(account);
        }
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        return user;
    }

    public User getUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    public User updateUser(long userId, UpdateUserRequest req) {
        User user = getUser(userId);
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname().trim());
        }
        if (req.getGender() != null && !req.getGender().isBlank()) {
            user.setGender(req.getGender());
        }
        if (req.getAge() != null) {
            user.setAge(req.getAge());
        }
        // 修改手机号需先验证短信验证码，并检查号段未被他人占用
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            smsService.validateCode(req.getPhone(), req.getCode());
            // 检查手机号是否已被其他用户占用
            User existing = userMapper.findByPhone(req.getPhone().trim());
            if (existing != null && !existing.getId().equals(user.getId())) {
                throw new BusinessException("该手机号已被其他用户使用");
            }
            user.setPhone(req.getPhone().trim());
        }
        if (req.getAvatar() != null && !req.getAvatar().isBlank()) {
            user.setAvatar(req.getAvatar().trim());
        }
        // 仅当填写了新密码时才更新，6-20位限制
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getNewPassword().length() < 6 || req.getNewPassword().length() > 20) {
                throw new BusinessException("新密码长度应为6-20位");
            }
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        }
        userMapper.updateById(user);
        return getUser(userId);
    }

    public void sendPhoneCode(long userId, String phone) {
        User current = getUser(userId);
        if (phone.equals(current.getPhone())) {
            throw new BusinessException("新手机号与当前手机号相同");
        }
        User existing = userMapper.findByPhone(phone);
        if (existing != null) {
            throw new BusinessException("该手机号已被其他用户使用");
        }
        smsService.sendCode(phone);
    }

    // 管理员手动变更用户状态（如限制/解封）
    public void updateUserStatus(long userId, String status, String reason) {
        User user = getUser(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }
}
