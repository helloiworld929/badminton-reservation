package com.badminton.controller;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.CreateReservationRequest;
import com.badminton.dto.response.CourtAvailabilityVO;
import com.badminton.dto.response.PageResult;
import com.badminton.dto.response.ReservationVO;
import com.badminton.entity.Reservation;
import com.badminton.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 球场预约控制器：查询可用时段、创建预约、取消预约、获取核销二维码、查看预约记录
@RestController
@RequestMapping
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // 查询指定日期和起始时段的场地可用情况
    @GetMapping("/reserve")
    public ApiResponse<List<CourtAvailabilityVO>> getAvailability(
            @RequestParam String date, @RequestParam int startTime) {
        return ApiResponse.success(reservationService.getAvailability(date, startTime));
    }

    // 创建预约（普通用户操作），自动绑定当前登录用户
    @PostMapping("/reserve")
    public ApiResponse<ReservationVO> create(@Valid @RequestBody CreateReservationRequest request,
                                              HttpSession session) {
        long userId = getUserId(session);
        return ApiResponse.success(reservationService.create(userId, request));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable Long id, HttpSession session) {
        long userId = getUserId(session);
        reservationService.cancel(userId, id);
        return ApiResponse.success(true);
    }

    // 获取预约核销二维码，仅本人可查看，过期时间根据预约开始时间计算
    @GetMapping("/reservations/{id}/qrcode")
    public ApiResponse<Map<String, Object>> getQrCode(@PathVariable Long id, HttpSession session) {
        Reservation r = reservationService.getById(id);
        long userId = getUserId(session);
        if (r.getUserId() != userId) {
            return ApiResponse.failure("无权操作该预约");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("reservationId", r.getId());
        data.put("verificationCode", r.getVerificationCode());
        data.put("qrcodeContent", "BMT:" + r.getId() + ":" + r.getVerificationCode());
        data.put("courtName", reservationService.getCourt(r.getCourtId()).getName());
        data.put("date", r.getReserveDate().toString());
        data.put("timeSlot", String.format("%02d:00-%02d:00", r.getStartTime(), r.getEndTime()));
        LocalDateTime reserveStart = LocalDateTime.of(r.getReserveDate(),
                java.time.LocalTime.of(r.getStartTime(), 0));
        data.put("expiresIn", java.time.Duration.between(LocalDateTime.now(), reserveStart).getSeconds());
        return ApiResponse.success(data);
    }

    // 分页查询当前用户的预约记录
    @GetMapping("/reservation-records")
    public ApiResponse<PageResult<ReservationVO>> getRecords(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize,
            HttpSession session) {
        long userId = getUserId(session);
        return ApiResponse.success(reservationService.findUserReservations(userId, pageNum, pageSize));
    }

    private long getUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        throw new IllegalArgumentException("请先登录");
    }
}
