package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.UpdateUserStatusRequest;
import com.badminton.dto.response.PageResult;
import com.badminton.entity.User;
import com.badminton.mapper.UserMapper;
import com.badminton.service.UserService;
import com.github.pagehelper.PageHelper;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

// 管理员用户管理：分页搜索用户列表、查看详情、修改用户状态（封禁/解封）
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final UserMapper userMapper;
    private final UserService userService;

    public AdminUserController(UserMapper userMapper, UserService userService) {
        this.userMapper = userMapper;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResult<User>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "5") int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<User> list = userMapper.findUsers(keyword, status);
        return ApiResponse.success(PageResult.of(list));
    }

    @GetMapping("/{id}")
    public ApiResponse<User> detail(@PathVariable Long id) {
        return ApiResponse.success(userService.getUser(id));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Boolean> updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody UpdateUserStatusRequest request) {
        userService.updateUserStatus(id, request.getStatus(), request.getReason());
        return ApiResponse.success(true);
    }
}
