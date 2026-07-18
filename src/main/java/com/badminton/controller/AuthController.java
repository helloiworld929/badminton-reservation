package com.badminton.controller;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.LoginRequest;
import com.badminton.entity.User;
import com.badminton.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

// 认证控制器：登录将会话信息写入HttpSession，登出则销毁Session
@RestController
@RequestMapping
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // 登录校验后创建Session，将userId和role存入会话
    @PostMapping("/login")
    public ApiResponse<User> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpReq) {
        User user = userService.login(request);
        HttpSession session = httpReq.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("role", user.getRole());
        return ApiResponse.success(user);
    }

    // 销毁当前Session实现登出
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest httpReq) {
        HttpSession session = httpReq.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success(true);
    }
}
