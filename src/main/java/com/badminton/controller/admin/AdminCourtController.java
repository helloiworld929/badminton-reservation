package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.CreateCourtRequest;
import com.badminton.dto.request.UpdateCourtStatusRequest;
import com.badminton.entity.Court;
import com.badminton.mapper.CourtMapper;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

// 管理员场地管理：增删改查场地，支持启用/禁用状态切换
@RestController
@RequestMapping("/admin/courts")
public class AdminCourtController {
    private final CourtMapper courtMapper;

    public AdminCourtController(CourtMapper courtMapper) {
        this.courtMapper = courtMapper;
    }

    // 获取全部场地列表
    @GetMapping
    public ApiResponse<List<Court>> list() {
        return ApiResponse.success(courtMapper.selectAll());
    }

    @PostMapping
    public ApiResponse<Court> create(@Valid @RequestBody CreateCourtRequest request) {
        Court court = new Court();
        court.setName(request.getName());
        court.setRemark(request.getRemark());
        court.setStatus(0);
        court.setCreatedAt(LocalDateTime.now());
        courtMapper.insert(court);
        return ApiResponse.success(court);
    }

    @PutMapping("/{id}")
    public ApiResponse<Court> update(@PathVariable Long id, @RequestBody CreateCourtRequest request) {
        Court court = courtMapper.selectById(id);
        if (court == null) return ApiResponse.failure("场地不存在");
        if (request.getName() != null) court.setName(request.getName());
        if (request.getRemark() != null) court.setRemark(request.getRemark());
        court.setUpdatedAt(LocalDateTime.now());
        courtMapper.updateById(court);
        return ApiResponse.success(court);
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Court> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateCourtStatusRequest request) {
        Court court = courtMapper.selectById(id);
        if (court == null) return ApiResponse.failure("场地不存在");
        court.setStatus(request.getStatus());
        court.setUpdatedAt(LocalDateTime.now());
        courtMapper.updateById(court);
        return ApiResponse.success(court);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        courtMapper.deleteById(id);
        return ApiResponse.success(true);
    }
}
