package com.badminton.config;

import com.badminton.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (path.startsWith(request.getContextPath())) {
            path = path.substring(request.getContextPath().length());
        }

        // 公开路径，无需登录
        if ("/login".equals(path)
                || "/login.html".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/register")
                || path.startsWith("/wechat/miniapp/login")
                || path.startsWith("/payments/callback")
                || "/favicon.ico".equals(path)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            // 管理端路径需要 admin 角色
            if (path.startsWith("/admin") && !"admin".equals(session.getAttribute("role"))) {
                if (isPageRequest(path)) {
                    response.sendRedirect(request.getContextPath() + "/index.html");
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(objectMapper.writeValueAsString(
                            ApiResponse.failure("无权限访问")));
                }
                return false;
            }
            return true;
        }

        // 检查小程序 Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (session != null && token.equals(session.getAttribute("token"))) {
                return true;
            }
        }

        if (isPageRequest(path)) {
            response.sendRedirect(request.getContextPath() + "/login.html");
            return false;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.unauthorized("请先登录")));
        return false;
    }

    private boolean isPageRequest(String path) {
        return path.isEmpty() || "/".equals(path) || path.endsWith(".html");
    }
}
