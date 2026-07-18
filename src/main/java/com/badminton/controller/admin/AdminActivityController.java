package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.common.BusinessException;
import com.badminton.dto.response.SignupVO;
import com.badminton.entity.Activity;
import com.badminton.entity.ActivitySignup;
import com.badminton.entity.User;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import com.badminton.mapper.UserMapper;
import com.badminton.service.OssService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 管理员活动管理：活动增删改查、图片上传、查看各活动报名人员列表
@RestController
@RequestMapping("/admin/activities")
public class AdminActivityController {
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final UserMapper userMapper;
    private final OssService ossService;

    public AdminActivityController(ActivityMapper activityMapper,
                                   ActivitySignupMapper signupMapper,
                                   UserMapper userMapper,
                                   OssService ossService) {
        this.activityMapper = activityMapper;
        this.signupMapper = signupMapper;
        this.userMapper = userMapper;
        this.ossService = ossService;
    }

    // ==== 活动增删改查 ====

    // 获取全部活动列表，同时统计每个活动的报名人数
    @GetMapping
    public ApiResponse<List<Activity>> list() {
        List<Activity> activities = activityMapper.selectAll();
        // 填充报名人数
        List<ActivitySignup> allSignups = signupMapper.selectAll();
        Map<Long, Long> countMap = allSignups.stream()
                .collect(Collectors.groupingBy(ActivitySignup::getActivityId, Collectors.counting()));
        for (Activity a : activities) {
            a.setSignupCount(countMap.getOrDefault(a.getId(), 0L).intValue());
        }
        return ApiResponse.success(activities);
    }

    @GetMapping("/{id}")
    public ApiResponse<Activity> detail(@PathVariable Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) throw new BusinessException("活动不存在");
        return ApiResponse.success(activity);
    }

    @PostMapping
    public ApiResponse<Activity> create(@RequestBody Activity activity) {
        activity.setId(null);
        activityMapper.insert(activity);
        return ApiResponse.success(activity);
    }

    @PutMapping("/{id}")
    public ApiResponse<Activity> update(@PathVariable Long id, @RequestBody Activity activity) {
        Activity existing = activityMapper.selectById(id);
        if (existing == null) throw new BusinessException("活动不存在");
        activity.setId(id);
        activityMapper.updateById(activity);
        return ApiResponse.success(activity);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        if (activityMapper.selectById(id) == null) throw new BusinessException("活动不存在");
        activityMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    // ==== 图片上传 ====

    @PostMapping("/upload-image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = ossService.upload(file, "activity");
            Map<String, String> data = new HashMap<>();
            data.put("url", url);
            return ApiResponse.success(data);
        } catch (IOException e) {
            throw new BusinessException("图片上传失败: " + e.getMessage());
        }
    }

    // ==== 报名列表 ====

    @GetMapping("/{id}/signups")
    public ApiResponse<List<SignupVO>> signups(@PathVariable Long id) {
        if (activityMapper.selectById(id) == null) throw new BusinessException("活动不存在");

        List<ActivitySignup> signups = signupMapper.findByActivityId(id);

        List<SignupVO> result = new ArrayList<>();
        for (ActivitySignup s : signups) {
            SignupVO vo = new SignupVO();
            vo.setId(s.getId());
            vo.setActivityId(s.getActivityId());
            vo.setUserId(s.getUserId());
            vo.setName(s.getName() != null ? s.getName() : "");
            vo.setPhone(s.getPhone() != null ? s.getPhone() : "");
            vo.setParticipantCount(s.getParticipantCount() != null ? s.getParticipantCount() : 0);
            if (s.getUserId() != null) {
                User user = userMapper.selectById(s.getUserId());
                if (user != null) {
                    vo.setNickname(user.getNickname() != null ? user.getNickname() : "");
                    vo.setAvatar(user.getAvatar() != null ? user.getAvatar() : "");
                }
            }
            result.add(vo);
        }
        return ApiResponse.success(result);
    }
}
