package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.dto.request.ActivitySignupRequest;
import com.badminton.entity.Activity;
import com.badminton.entity.ActivitySignup;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import org.springframework.stereotype.Service;

import java.util.List;

// 活动业务：查询活动列表、用户报名
@Service
public class ActivityService {
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper activitySignupMapper;

    public ActivityService(ActivityMapper activityMapper, ActivitySignupMapper activitySignupMapper) {
        this.activityMapper = activityMapper;
        this.activitySignupMapper = activitySignupMapper;
    }

    public List<Activity> findAll() {
        return activityMapper.selectAll();
    }

    // 活动报名：校验活动存在性并防止重复报名
    public void signup(long userId, ActivitySignupRequest req) {
        if (activityMapper.selectById(req.getActivityId()) == null) {
            throw new BusinessException("活动不存在");
        }

        List<ActivitySignup> existing = activitySignupMapper.findByActivityIdAndUserId(
                req.getActivityId(), userId);
        if (!existing.isEmpty()) {
            throw new BusinessException("您已报名该活动，请勿重复报名");
        }

        ActivitySignup signup = new ActivitySignup();
        signup.setActivityId(req.getActivityId());
        signup.setUserId(userId);
        signup.setName(req.getName().trim());
        signup.setPhone(req.getPhone().trim());
        activitySignupMapper.insert(signup);
    }
}
