package com.badminton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 定时任务调度：定期执行爽约标记等后台任务
@Component
// 测试 profile 通过 app.scheduling.enabled=false 关闭定时任务，避免后台任务干扰测试数据。
@ConditionalOnProperty(value = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTaskService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final ReservationService reservationService;

    public ScheduledTaskService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // 每5分钟执行一次：扫描超时未核销的预约，标记为爽约，累计2次限制预约
    @Scheduled(fixedRate = 300000)
    public void markNoshowReservations() {
        try {
            reservationService.markNoshow();
        } catch (Exception e) {
            log.error("标记爽约预约失败", e);
        }
    }
}
