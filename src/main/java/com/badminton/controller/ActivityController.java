package com.badminton.controller;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.ActivitySignupRequest;
import com.badminton.entity.Activity;
import com.badminton.service.ActivityService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

// 活动控制器（用户端）：浏览活动列表、报名参加活动
@RestController
@RequestMapping
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/activities")
    public ApiResponse<List<Activity>> getActivities() {
        return ApiResponse.success(activityService.findAll());
    }

    @PostMapping("/activity-signups")
    public ApiResponse<Boolean> signup(@Valid @RequestBody ActivitySignupRequest request,
                                        HttpSession session) {
        long userId = getUserId(session);
        activityService.signup(userId, request);
        return ApiResponse.success(true);
    }

    private long getUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number) return ((Number) userId).longValue();
        throw new IllegalArgumentException("请先登录");
    }
}
