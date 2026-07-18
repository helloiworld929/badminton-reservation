package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.VerifyRequest;
import com.badminton.dto.response.PageResult;
import com.badminton.dto.response.ReservationVO;
import com.badminton.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

// 管理员预约管理：分页查询所有预约、查看详情、扫码核销（支持BMT前缀解析）
@RestController
@RequestMapping("/admin")
public class AdminReservationController {
    private final ReservationService reservationService;

    public AdminReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/reservations")
    public ApiResponse<PageResult<ReservationVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long courtId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(reservationService.adminList(status, date, courtId, pageNum, pageSize));
    }

    @GetMapping("/reservations/{id}")
    public ApiResponse<ReservationVO> detail(@PathVariable Long id) {
        return ApiResponse.success(reservationService.adminGetDetail(id));
    }

    // 核销预约：支持直接传码或扫描二维码内容（BMT:id:code格式），自动解析
    @PostMapping("/reservations/verify")
    public ApiResponse<ReservationVO> verify(@RequestBody VerifyRequest request, HttpSession session) {
        long operatorId = getUserId(session);
        if (request.getScanContent() != null && request.getScanContent().startsWith("BMT:")) {
            String[] parts = request.getScanContent().split(":");
            if (parts.length >= 3) {
                request.setReservationId(Long.parseLong(parts[1]));
                request.setCode(parts[2]);
            }
        }
        return ApiResponse.success(reservationService.verify(operatorId, request.getCode(), request.getReservationId()));
    }

    private long getUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number) return ((Number) userId).longValue();
        throw new IllegalArgumentException("请先登录");
    }
}
