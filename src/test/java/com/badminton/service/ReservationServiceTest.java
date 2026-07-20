package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.config.ReservationProperties;
import com.badminton.dto.request.CreateReservationRequest;
import com.badminton.dto.response.CourtAvailabilityVO;
import com.badminton.dto.response.ReservationVO;
import com.badminton.entity.Court;
import com.badminton.entity.Reservation;
import com.badminton.entity.User;
import com.badminton.mapper.CourtMapper;
import com.badminton.mapper.OperationLogMapper;
import com.badminton.mapper.ReservationMapper;
import com.badminton.mapper.UserMapper;
import com.aliyun.oss.OSS;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(ReservationServiceTest.FixedClockConfiguration.class)
/**
 * 预约核心流程测试：启动 Spring 上下文，但用 MockBean 隔离数据库、Redis 和云服务。
 */
class ReservationServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationProperties reservationProperties;

    @MockBean
    private ReservationMapper reservationMapper;

    @MockBean
    private CourtMapper courtMapper;

    @MockBean
    private OperationLogMapper operationLogMapper;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private OSS ossClient;

    @MockBean
    private AsyncClient smsAsyncClient;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    // 事务边界仍由生产代码声明，测试中只隔离真实数据库连接。
    @MockBean
    private PlatformTransactionManager transactionManager;

    @Test
    // 验证可用场地会补齐空位，并正确计算场地状态。
    void getAvailabilityFillsOpenCourtSlots() {
        Court court = court(1L, 0);
        Reservation player = new Reservation();
        player.setCourtId(1L);
        player.setUserGender("男");
        player.setUserNickname("测试用户");

        when(courtMapper.selectAll()).thenReturn(Collections.singletonList(court));
        when(reservationMapper.findBySlot(TOMORROW, 12)).thenReturn(Collections.singletonList(player));

        List<CourtAvailabilityVO> result = reservationService.getAvailability(TOMORROW.toString(), 12);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getStatus());
        assertEquals("可预约", result.get(0).getStatusDisplay());
        assertEquals(4, result.get(0).getPlayers().size());
        assertEquals(1, result.get(0).getPlayers().get(0).getGender());
    }

    @Test
    // 受限用户不能创建新预约，且不应继续查询场地。
    void createRejectsRestrictedUser() {
        User user = new User();
        user.setStatus("restricted");
        when(userMapper.selectById(7L)).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.create(7L, request(1L, TOMORROW, 12, 13)));

        assertEquals("您的账户已被限制，无法预约，请联系管理员", exception.getMessage());
        verify(courtMapper, never()).selectById(anyLong());
    }

    @Test
    // 每天开放预约时间之前，创建预约请求应被拒绝。
    void createRejectsBeforeDailyOpenTime() {
        reservationProperties.setDailyOpenTime(LocalTime.of(14, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.create(7L, request(1L, TOMORROW, 12, 13)));

        assertEquals("每天 14:00 后才开始接受预约", exception.getMessage());
        reservationProperties.setDailyOpenTime(LocalTime.MIDNIGHT);
    }

    @Test
    // 成功创建预约时应生成六位核销码并记录操作日志。
    void createStoresReservationAndOperationLog() {
        User user = new User();
        user.setStatus("active");
        Court court = court(1L, 0);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(courtMapper.selectById(1L)).thenReturn(court);
        when(reservationMapper.countActiveByCourtSlot(1L, TOMORROW, 12)).thenReturn(0);
        when(reservationMapper.findByUser(7L)).thenReturn(Collections.emptyList());
        when(reservationMapper.insert(any(Reservation.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Reservation.class).setId(100L);
            return 1;
        });

        ReservationVO result = reservationService.create(7L, request(1L, TOMORROW, 12, 13));

        assertEquals(100L, result.getId());
        assertEquals("unverified", result.getStatus());
        assertNotNull(result.getVerificationCode());
        assertTrue(result.getVerificationCode().matches("[0-9]{6}"));
        verify(operationLogMapper).insert(any());
    }

    @Test
    // 用户只能取消自己的未核销预约。
    void cancelUpdatesOwnedReservation() {
        Reservation reservation = new Reservation();
        reservation.setId(100L);
        reservation.setUserId(7L);
        reservation.setStatus("unverified");
        when(reservationMapper.selectById(100L)).thenReturn(reservation);

        reservationService.cancel(7L, 100L);

        assertEquals("cancelled", reservation.getStatus());
        verify(reservationMapper).updateById(reservation);
        verify(operationLogMapper).insert(any());
    }

    @Test
    // 预约开始前超过提前核销窗口时，核销请求应被拒绝。
    void verifyRejectsTooEarlyCheckin() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 12, "123456");
        when(reservationMapper.findByVerificationCode("123456")).thenReturn(reservation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.verify(9L, "123456", null));

        assertEquals("尚未到核销时间", exception.getMessage());
        verify(reservationMapper, never()).updateById(any(Reservation.class));
    }

    @Test
    // 进入核销窗口后，预约状态和核销人信息应被更新。
    void verifyUpdatesReservationInsideCheckinWindow() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 10, "123456");
        when(reservationMapper.findByVerificationCode("123456")).thenReturn(reservation);
        when(courtMapper.selectById(1L)).thenReturn(court(1L, 0));

        ReservationVO result = reservationService.verify(9L, "123456", null);

        assertEquals("verified", result.getStatus());
        assertEquals("verified", reservation.getStatus());
        assertEquals(9L, reservation.getVerifiedBy());
        verify(reservationMapper).updateById(reservation);
        verify(operationLogMapper).insert(any());
    }

    @Test
    // 第二次爽约后，用户状态应被限制。
    void markNoshowRestrictsUserAfterSecondMiss() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 9, "123456");
        User user = new User();
        user.setId(7L);
        user.setNoshowCount(1);
        user.setStatus("active");
        when(reservationMapper.findNoshowCandidates("2026-07-20 09:30:00"))
                .thenReturn(Collections.singletonList(reservation));
        when(userMapper.selectById(7L)).thenReturn(user);

        reservationService.markNoshow();

        assertEquals("noshow", reservation.getStatus());
        assertEquals(2, user.getNoshowCount());
        assertEquals("restricted", user.getStatus());
        verify(reservationMapper).updateById(reservation);
        verify(userMapper).updateById(user);
    }

    private static CreateReservationRequest request(long courtId, LocalDate date, int start, int end) {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setCourtId(courtId);
        request.setDate(date.toString());
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    private static Court court(long id, int status) {
        Court court = new Court();
        court.setId(id);
        court.setName("测试场地");
        court.setStatus(status);
        return court;
    }

    private static Reservation reservation(long id, long userId, long courtId,
                                           LocalDate date, int start, String code) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUserId(userId);
        reservation.setCourtId(courtId);
        reservation.setReserveDate(date);
        reservation.setStartTime(start);
        reservation.setEndTime(start + 1);
        reservation.setStatus("unverified");
        reservation.setVerificationCode(code);
        return reservation;
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        // 固定测试时间为 2026-07-20 10:00（上海时区），避免测试依赖真实系统时间。
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZONE);
        }
    }
}
