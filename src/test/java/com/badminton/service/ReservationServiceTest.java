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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        player.setUserAge(22);

        when(courtMapper.selectAll()).thenReturn(Collections.singletonList(court));
        when(reservationMapper.findBySlot(TOMORROW, 12)).thenReturn(Collections.singletonList(player));

        List<CourtAvailabilityVO> result = reservationService.getAvailability(TOMORROW.toString(), 12);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getStatus());
        assertEquals("可预约", result.get(0).getStatusDisplay());
        assertEquals(4, result.get(0).getPlayers().size());
        assertEquals(1, result.get(0).getPlayers().get(0).getGender());
        assertEquals(22, result.get(0).getPlayers().get(0).getAge());
    }

    @Test
    // 受限用户不能创建新预约，且不应继续查询场地。
    void createRejectsRestrictedUser() {
        User user = new User();
        user.setStatus("restricted");
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.create(7L, request(1L, TOMORROW, 12, 13)));

        assertEquals("您的账户已被限制，无法预约，请联系管理员", exception.getMessage());
        verify(courtMapper, never()).selectByIdForUpdate(anyLong());
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
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(courtMapper.selectByIdForUpdate(1L)).thenReturn(court);
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
    // 两个用户同时看到同一个剩余名额时，场地人数不能超过配置上限。
    void concurrentReservationsCannotExceedCourtCapacity() throws Exception {
        Court court = court(1L, 0);
        AtomicInteger persistedReservations = new AtomicInteger(3);
        CountDownLatch bothRequestsCounted = new CountDownLatch(2);
        ReentrantLock courtRowLock = new ReentrantLock();

        when(userMapper.selectByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            User user = new User();
            user.setId(invocation.getArgument(0, Long.class));
            user.setStatus("active");
            return user;
        });
        when(courtMapper.selectByIdForUpdate(1L)).thenAnswer(invocation -> {
            courtRowLock.lock();
            return court;
        });
        when(reservationMapper.findByUser(anyLong())).thenReturn(Collections.emptyList());
        when(reservationMapper.countActiveByCourtSlot(1L, TOMORROW, 12))
                .thenAnswer(invocation -> {
                    // 捕获读取时的同一个快照，再放行两个插入请求。
                    int observedCount = persistedReservations.get();
                    bothRequestsCounted.countDown();
                    bothRequestsCounted.await(250, TimeUnit.MILLISECONDS);
                    if (observedCount >= 4 && courtRowLock.isHeldByCurrentThread()) {
                        courtRowLock.unlock();
                    }
                    return observedCount;
                });
        when(reservationMapper.insert(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0, Reservation.class);
            reservation.setId((long) persistedReservations.incrementAndGet());
            if (courtRowLock.isHeldByCurrentThread()) {
                courtRowLock.unlock();
            }
            return 1;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationVO> first = executor.submit(
                    () -> reservationService.create(7L, request(1L, TOMORROW, 12, 13)));
            Future<ReservationVO> second = executor.submit(
                    () -> reservationService.create(8L, request(1L, TOMORROW, 12, 13)));

            for (Future<ReservationVO> result : List.of(first, second)) {
                try {
                    result.get();
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof BusinessException)) {
                        throw e;
                    }
                }
            }

            assertEquals(4, persistedReservations.get(),
                    "场地有效预约人数不能超过4人");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    // 用户只能取消自己的未核销预约。
    void cancelUpdatesOwnedReservation() {
        Reservation reservation = new Reservation();
        reservation.setId(100L);
        reservation.setUserId(7L);
        reservation.setStatus("unverified");
        when(reservationMapper.selectById(100L)).thenReturn(reservation);
        when(reservationMapper.transitionStatus(
                100L, "unverified", "cancelled", null, null)).thenReturn(1);

        reservationService.cancel(7L, 100L);

        assertEquals("cancelled", reservation.getStatus());
        verify(reservationMapper).transitionStatus(
                100L, "unverified", "cancelled", null, null);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void cancelRejectsNoshowReservation() {
        Reservation reservation = new Reservation();
        reservation.setId(100L);
        reservation.setUserId(7L);
        reservation.setStatus("noshow");
        when(reservationMapper.selectById(100L)).thenReturn(reservation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.cancel(7L, 100L));

        assertEquals("当前预约状态不可取消", exception.getMessage());
        assertEquals("noshow", reservation.getStatus());
        verify(reservationMapper, never()).transitionStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    // 预约开始前超过提前核销窗口时，核销请求应被拒绝。
    void verifyRejectsTooEarlyCheckin() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 12, "123456");
        when(reservationMapper.findByVerificationCode("123456")).thenReturn(reservation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.verify(9L, "123456", null));

        assertEquals("尚未到核销时间", exception.getMessage());
        verify(reservationMapper, never()).transitionStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void verifyDemoCodeReturnsSimulatedSuccessWithoutChangingReservation() {
        ReservationVO result = reservationService.verify(9L, "888888", null);

        assertEquals(0L, result.getId());
        assertEquals("verified", result.getStatus());
        verify(reservationMapper, never()).transitionStatus(anyLong(), any(), any(), any(), any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    // 进入核销窗口后，预约状态和核销人信息应被更新。
    void verifyUpdatesReservationInsideCheckinWindow() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 10, "123456");
        when(reservationMapper.findByVerificationCode("123456")).thenReturn(reservation);
        when(courtMapper.selectByIdIncludingDeleted(1L)).thenReturn(court(1L, 0));
        when(reservationMapper.transitionStatus(
                100L, "unverified", "verified",
                LocalDateTime.of(TODAY, LocalTime.of(10, 0)), 9L)).thenReturn(1);

        ReservationVO result = reservationService.verify(9L, "123456", null);

        assertEquals("verified", result.getStatus());
        assertEquals("测试场地", result.getCourtName());
        assertEquals("verified", reservation.getStatus());
        assertEquals(9L, reservation.getVerifiedBy());
        verify(reservationMapper).transitionStatus(
                100L, "unverified", "verified",
                LocalDateTime.of(TODAY, LocalTime.of(10, 0)), 9L);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void verifyRejectsNoshowReservation() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 10, "123456");
        reservation.setStatus("noshow");
        when(reservationMapper.findByVerificationCode("123456")).thenReturn(reservation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.verify(9L, "123456", null));

        assertEquals("当前预约状态不可核销", exception.getMessage());
        verify(reservationMapper, never()).transitionStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void verifyRejectsReservationAfterNoshowGracePeriod() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 9, "123456");
        when(reservationMapper.findByVerificationCode("123456")).thenReturn(reservation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reservationService.verify(9L, "123456", null));

        assertEquals("已超过核销时间", exception.getMessage());
        verify(reservationMapper, never()).transitionStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    // 第二次爽约后，用户状态应被限制。
    void markNoshowRestrictsUserAfterSecondMiss() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 9, "123456");
        when(reservationMapper.findNoshowCandidates("2026-07-20 09:30:00"))
                .thenReturn(Collections.singletonList(reservation));
        when(reservationMapper.transitionStatus(
                100L, "unverified", "noshow", null, null)).thenReturn(1);
        when(userMapper.incrementNoshowCountAndRestrict(7L, 2)).thenReturn(1);

        reservationService.markNoshow();

        assertEquals("noshow", reservation.getStatus());
        verify(reservationMapper).transitionStatus(
                100L, "unverified", "noshow", null, null);
        verify(userMapper).incrementNoshowCountAndRestrict(7L, 2);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void markNoshowSkipsSideEffectsWhenAnotherWorkerAlreadyUpdatedReservation() {
        Reservation reservation = reservation(100L, 7L, 1L, TODAY, 9, "123456");
        when(reservationMapper.findNoshowCandidates("2026-07-20 09:30:00"))
                .thenReturn(Collections.singletonList(reservation));
        when(reservationMapper.transitionStatus(
                100L, "unverified", "noshow", null, null)).thenReturn(0);

        reservationService.markNoshow();

        verify(userMapper, never()).incrementNoshowCountAndRestrict(anyLong(), anyInt());
        verify(operationLogMapper, never()).insert(any());
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
