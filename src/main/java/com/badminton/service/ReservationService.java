package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.config.ReservationProperties;
import com.badminton.dto.request.CreateReservationRequest;
import com.badminton.dto.response.CourtAvailabilityVO;
import com.badminton.dto.response.PageResult;
import com.badminton.dto.response.ReservationVO;
import com.badminton.entity.*;
import com.badminton.mapper.*;
import com.github.pagehelper.PageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

// 预约业务：场地可用性查询、预约创建/取消/核销，含爽约自动标记
@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final String DEFAULT_AVATAR = "https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReservationProperties properties;
    private final Clock clock;
    private final ReservationMapper reservationMapper;
    private final CourtMapper courtMapper;
    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    public ReservationService(ReservationMapper reservationMapper, CourtMapper courtMapper,
                              OperationLogMapper operationLogMapper, UserMapper userMapper,
                              ReservationProperties properties, Clock clock) {
        this.reservationMapper = reservationMapper;
        this.courtMapper = courtMapper;
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
        this.properties = properties;
        this.clock = clock;
    }

    // ==== 查询可用场地 ====
    // 根据日期和时间段，返回每个场地的预约人数及状态（可预约/已满/锁定/维护中）
    public List<CourtAvailabilityVO> getAvailability(String dateStr, int startTime) {
        LocalDate date = parseDate(dateStr);
        validateStartTime(startTime);
        List<Court> courts = courtMapper.selectAll();
        List<Reservation> reservations = reservationMapper.findBySlot(date, startTime);
        Map<Long, List<Reservation>> byCourt = reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getCourtId));

        List<CourtAvailabilityVO> result = new ArrayList<>();
        for (Court court : courts) {
            CourtAvailabilityVO vo = new CourtAvailabilityVO();
            vo.setCourtId(court.getId());
            vo.setName(court.getName());
            vo.setRemark(court.getRemark());

            List<Reservation> courtRes = byCourt.getOrDefault(court.getId(), List.of());
            List<CourtAvailabilityVO.PlayerSlotVO> players = new ArrayList<>();
            for (Reservation r : courtRes) {
                String avatar = r.getUserAvatar() != null ? r.getUserAvatar() : DEFAULT_AVATAR;
                int gender = genderCode(r.getUserGender());
                String nickname = r.getUserNickname() != null ? r.getUserNickname() : "";
                players.add(new CourtAvailabilityVO.PlayerSlotVO(avatar, gender, nickname, r.getUserAge()));
            }
    // 不足最大人数的位置补空位占位
            while (players.size() < properties.getMaxPlayersPerCourt()) {
                players.add(new CourtAvailabilityVO.PlayerSlotVO(DEFAULT_AVATAR, 0, null, null));
            }
            vo.setPlayers(players);

            if (court.getStatus() != null && court.getStatus() == 1) {
                vo.setStatus(1);
                vo.setStatusDisplay("已锁定");
            } else if (court.getStatus() != null && court.getStatus() == 2) {
                vo.setStatus(2);
                vo.setStatusDisplay("维护中");
            } else if (courtRes.size() >= properties.getMaxPlayersPerCourt()) {
                vo.setStatus(3);
                vo.setStatusDisplay("已满");
            } else {
                vo.setStatus(0);
                vo.setStatusDisplay("可预约");
            }
            result.add(vo);
        }
        return result;
    }

    // ==== 创建预约 ====
    // 含多项校验：时间范围（过去/未来>2天不允）、场地状态、人数上限、用户每日最多2次、总共最多4个未核销、同时间段不可重复
    @Transactional
    public ReservationVO create(long userId, CreateReservationRequest req) {
        validateDailyOpenTime();
        LocalDate date = parseDate(req.getDate());
        int startTime = req.getStartTime();
        int endTime = req.getEndTime();
        validateStartTime(startTime);
        if (endTime != startTime + 1) {
            throw new BusinessException("每次只能预约1小时");
        }

        // 拒绝过去时间段的预约
        LocalDateTime reserveStart = LocalDateTime.of(date, LocalTime.of(startTime, 0));
        if (reserveStart.isBefore(now())) {
            throw new BusinessException("不能预约过去的时间段");
        }

        // 只能预约今天、明天、后天
        if (date.isAfter(today().plusDays(2))) {
            throw new BusinessException("只能预约今天、明天或后天");
        }

        // 检查用户是否被限制
        // 固定先锁用户，再锁场地，串行化同一用户的预约限制和同一场地的容量检查。
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("restricted".equals(user.getStatus())) {
            throw new BusinessException("您的账户已被限制，无法预约，请联系管理员");
        }

        Court court = courtMapper.selectByIdForUpdate(req.getCourtId());
        if (court == null) throw new BusinessException("场地不存在");
        if (court.getStatus() != null && court.getStatus() != 0)
            throw new BusinessException("该场地暂不可预约");

        int count = reservationMapper.countActiveByCourtSlot(req.getCourtId(), date, startTime);
        if (count >= properties.getMaxPlayersPerCourt()) {
            throw new BusinessException("该场地当前时间段已满" + properties.getMaxPlayersPerCourt() + "人");
        }

        // 检查预约限制
        List<Reservation> existing = reservationMapper.findByUser(userId);
        List<Reservation> active = existing.stream()
                .filter(r -> !"cancelled".equals(r.getStatus())
                        && !"noshow".equals(r.getStatus())
                        && !"verified".equals(r.getStatus()))
                .collect(Collectors.toList());

        // 每日最多预约 2 次

        long todayCount = active.stream()
                .filter(r -> date.equals(r.getReserveDate()))
                .count();
        if (todayCount >= 2) {
            throw new BusinessException("您今天已预约" + todayCount + "次，每天最多预约2次");
        }

        // 未核销预约总数不超过 4 个

        if (active.size() >= 4) {
            throw new BusinessException("您当前有" + active.size() + "个未验证预约，最多可预约4个");
        }

        // 同一时间段不能预约多个场地
        boolean sameSlot = active.stream().anyMatch(r ->
                r.getReserveDate().equals(date) && r.getStartTime() == startTime);
        if (sameSlot) throw new BusinessException("该时间段你已预约了其他场地，请勿重复预约");

        // 含已取消的也不能重约，防止反复预约取消

        boolean duplicate = existing.stream().anyMatch(r ->
                r.getCourtId().equals(req.getCourtId())
                        && r.getReserveDate().equals(date)
                        && r.getStartTime() == startTime);
        if (duplicate) throw new BusinessException("你已预约过该场地的这个时间段（含已取消），不可重复预约");

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setCourtId(req.getCourtId());
        reservation.setReserveDate(date);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setStatus("unverified");
        reservation.setVerificationCode(randomCode()); // 6位随机核销码

        reservation.setCreatedAt(now());
        reservationMapper.insert(reservation);

        logOperation(reservation.getId(), userId, null, "create", "创建预约");

        return toVO(reservation, court);
    }

    // ==== 取消预约 ====
    @Transactional
    public void cancel(long userId, long reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) throw new BusinessException("预约不存在");
        if (reservation.getUserId() != userId) throw new BusinessException("无权操作该预约");

        String status = reservation.getStatus();
        if (!"unverified".equals(status)) {
            throw new BusinessException("当前预约状态不可取消");
        }

        int updated = reservationMapper.transitionStatus(
                reservationId, "unverified", "cancelled", null, null);
        if (updated != 1) {
            throw new BusinessException("预约状态已发生变化，请刷新后重试");
        }
        reservation.setStatus("cancelled");

        logOperation(reservationId, userId, null, "cancel", "取消预约");
    }

    // ==== 核销（签到） ====
    // 通过核销码或预约ID查找预约，校验时间窗口（预约开始前N分钟才可核销），更新状态
    @Transactional
    public ReservationVO verify(long operatorId, String code, Long reservationId) {
        // 演示核销码：用于前端展示核销成功效果，不修改真实预约记录。
        if ("888888".equals(code)) {
            ReservationVO vo = new ReservationVO();
            vo.setId(0L);
            vo.setCourtName("测试场地");
            vo.setReserveDate(today().toString());
            vo.setStartTime(10);
            vo.setEndTime(11);
            vo.setStatus("verified");
            vo.setStatusDisplay("已验证");
            vo.setCreatedAt(now().format(DT_FMT));
            return vo;
        }

        Reservation reservation;
        if (reservationId != null) {
            reservation = reservationMapper.selectById(reservationId);
        } else {
            reservation = reservationMapper.findByVerificationCode(code);
        }

        if (reservation == null) throw new BusinessException("验证码错误或预约不存在");
        if (code != null && !code.equals(reservation.getVerificationCode()))
            throw new BusinessException("验证码错误");
        if (!"unverified".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约状态不可核销");
        }

        // 检查时间窗口
        LocalDateTime reserveStart = LocalDateTime.of(reservation.getReserveDate(),
                LocalTime.of(reservation.getStartTime(), 0));
        LocalDateTime currentTime = now();
        LocalDateTime earliestCheckin = reserveStart.minusMinutes(properties.getCheckinAdvanceMinutes());
        LocalDateTime latestCheckin = reserveStart.plusMinutes(properties.getNoshowGraceMinutes());
        if (currentTime.isBefore(earliestCheckin)) {
            throw new BusinessException("尚未到核销时间");
        }
        if (currentTime.isAfter(latestCheckin)) {
            throw new BusinessException("已超过核销时间");
        }

        int updated = reservationMapper.transitionStatus(
                reservation.getId(), "unverified", "verified", currentTime, operatorId);
        if (updated != 1) {
            throw new BusinessException("预约状态已发生变化，请刷新后重试");
        }
        reservation.setStatus("verified");
        reservation.setVerifiedAt(currentTime);
        reservation.setVerifiedBy(operatorId);

        logOperation(reservation.getId(), reservation.getUserId(), operatorId, "verify", "已核销");

        Court court = courtMapper.selectByIdIncludingDeleted(reservation.getCourtId());
        return toVO(reservation, court);
    }

    // ==== 查询用户预约列表 ====
    public List<ReservationVO> findUserReservations(long userId) {
        List<Reservation> list = reservationMapper.findByUser(userId);
        return list.stream().map(r -> toVO(r, null)).collect(Collectors.toList());
    }

    /** 分页版本 */
    public PageResult<ReservationVO> findUserReservations(long userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Reservation> rawList = reservationMapper.findByUser(userId);
        List<ReservationVO> voList = rawList.stream().map(r -> toVO(r, null)).collect(Collectors.toList());
        return PageResult.of(rawList, voList);
    }

    // ==== 管理端：查询全部（支持筛选） ====
    public List<ReservationVO> adminList(String status, String date, Long courtId) {
        List<Reservation> list = reservationMapper.findAdminList(status, date, courtId);
        return list.stream().map(r -> toVO(r, null)).collect(Collectors.toList());
    }

    /** 管理端分页版本 */
    public PageResult<ReservationVO> adminList(String status, String date, Long courtId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Reservation> rawList = reservationMapper.findAdminList(status, date, courtId);
        List<ReservationVO> voList = rawList.stream().map(r -> toVO(r, null)).collect(Collectors.toList());
        return PageResult.of(rawList, voList);
    }

    // ==== 管理端：查询详情 ====
    public ReservationVO adminGetDetail(long reservationId) {
        Reservation detail = reservationMapper.findDetailById(reservationId);
        if (detail == null) throw new BusinessException("预约不存在");
        ReservationVO vo = toVO(detail, null);
        vo.setTimeline(operationLogMapper.findByReservationId(reservationId));
        return vo;
    }

    // ==== 定时任务：自动标记爽约 ====
    // 超过预约时间后仍未核销的标记为"爽约"，累计2次爽约则限制用户预约权限
    @Transactional
    public void markNoshow() {
        LocalDateTime cutoff = now().minusMinutes(properties.getNoshowGraceMinutes());
        String cutoffStr = cutoff.format(DT_FMT);
        List<Reservation> unverifiedList = reservationMapper.findNoshowCandidates(cutoffStr);
        for (Reservation r : unverifiedList) {
            int updated = reservationMapper.transitionStatus(
                    r.getId(), "unverified", "noshow", null, null);
            if (updated != 1) {
                continue;
            }
            r.setStatus("noshow");

            int userUpdated = userMapper.incrementNoshowCountAndRestrict(r.getUserId(), 2);
            if (userUpdated != 1) {
                throw new IllegalStateException("爽约预约关联的用户不存在: " + r.getUserId());
            }

            logOperation(r.getId(), r.getUserId(), null, "noshow", "预约超时未核销");
            log.info("已标记爽约: 预约ID={}", r.getId());
        }
    }

    // ==== 辅助方法 ====
    private ReservationVO toVO(Reservation r, Court court) {
        ReservationVO vo = new ReservationVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setCourtId(r.getCourtId());
        vo.setCourtName(r.getCourtName() != null ? r.getCourtName()
                : (court != null ? court.getName() : ""));
        vo.setReserveDate(r.getReserveDate() != null ? r.getReserveDate().toString() : null);
        vo.setStartTime(r.getStartTime());
        vo.setEndTime(r.getEndTime());
        vo.setStatus(r.getStatus());
        vo.setStatusDisplay(statusDisplay(r.getStatus()));
        vo.setVerificationCode(r.getVerificationCode());
        vo.setUserAvatar(r.getUserAvatar());
        vo.setUserGender(r.getUserGender());
        vo.setUserNickname(r.getUserNickname());
        vo.setUserPhone(r.getUserPhone());
        vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(DT_FMT) : null);
        vo.setVerifiedAt(r.getVerifiedAt() != null ? r.getVerifiedAt().format(DT_FMT) : null);
        return vo;
    }

    private String statusDisplay(String status) {
        switch (status) {
            case "unverified": return "未验证";
            case "verified": return "已验证";
            case "noshow": return "爽约";
            case "cancelled": return "已取消";
            default: return status;
        }
    }

    private int genderCode(String gender) {
        if ("男".equals(gender)) return 1;
        if ("女".equals(gender)) return 2;
        return 0;
    }

    private String randomCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) throw new BusinessException("预约日期不能为空");
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new BusinessException("预约日期格式必须为 yyyy-MM-dd");
        }
    }

    private void validateStartTime(int startTime) {
        if (startTime < properties.getStartHour() || startTime > properties.getEndHour())
            throw new BusinessException("预约开始时间必须在 " + String.format("%02d", properties.getStartHour())
                    + ":00 到 " + properties.getEndHour() + ":00 之间");
    }

    /** 每天达到开放时间后才允许创建预约，查询场地可用性不受影响。 */
    private void validateDailyOpenTime() {
        LocalTime currentTime = LocalTime.now(clock);
        if (currentTime.isBefore(properties.getDailyOpenTime())) {
            throw new BusinessException("每天 " + properties.getDailyOpenTime()
                    + " 后才开始接受预约");
        }
    }

    private void logOperation(Long reservationId, Long userId, Long operatorId, String action, String detail) {
        OperationLog oplog = new OperationLog();
        oplog.setReservationId(reservationId);
        oplog.setUserId(userId);
        oplog.setOperatorId(operatorId);
        oplog.setAction(action);
        oplog.setDetail(detail);
        oplog.setCreatedAt(now());
        operationLogMapper.insert(oplog);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    public Reservation getById(long reservationId) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) throw new BusinessException("预约不存在");
        return r;
    }

    public Court getCourt(long courtId) {
        return courtMapper.selectByIdIncludingDeleted(courtId);
    }
}
