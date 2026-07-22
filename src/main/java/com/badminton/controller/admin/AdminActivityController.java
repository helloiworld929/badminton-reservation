package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.common.BusinessException;
import com.badminton.dto.response.SignupVO;
import com.badminton.entity.Activity;
import com.badminton.entity.ActivitySignup;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import com.badminton.service.ActivitySignupExcelService;
import com.badminton.service.OssService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final OssService ossService;
    private final ActivitySignupExcelService excelService;

    public AdminActivityController(ActivityMapper activityMapper,
                                   ActivitySignupMapper signupMapper,
                                   OssService ossService,
                                   ActivitySignupExcelService excelService) {
        this.activityMapper = activityMapper;
        this.signupMapper = signupMapper;
        this.ossService = ossService;
        this.excelService = excelService;
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
        if (activityMapper.softDeleteById(id) != 1) {
            throw new BusinessException("活动删除失败，请刷新后重试");
        }
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
        requireActivity(id);
        return ApiResponse.success(findSignupDetails(id));
    }

    @GetMapping("/{id}/signups/export")
    public ResponseEntity<byte[]> exportSignups(@PathVariable Long id) {
        Activity activity = requireActivity(id);
        try {
            byte[] content = excelService.export(activity.getTitle(), findSignupDetails(id));
            String filename = sanitizeFilename(activity.getTitle()) + "-报名名单.xlsx";
            String encodedFilename = UriUtils.encode(filename, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(content.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(content);
        } catch (IOException e) {
            throw new BusinessException("报名名单导出失败");
        }
    }

    private List<SignupVO> findSignupDetails(Long activityId) {
        return signupMapper.findDetailsByActivityId(activityId);
    }

    private Activity requireActivity(Long id) {
        Activity activity = activityMapper.selectByIdIncludingDeleted(id);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        return activity;
    }

    private String sanitizeFilename(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "活动";
        }
        return title.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
    }
}
