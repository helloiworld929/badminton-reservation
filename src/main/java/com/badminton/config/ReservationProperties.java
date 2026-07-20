package com.badminton.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 预约规则配置，集中管理并支持在测试中直接构造。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.reservation")
public class ReservationProperties {
    /** 每块场地允许的最大预约人数。 */
    private int maxPlayersPerCourt = 4;
    /** 可预约的开始小时（24小时制）。 */
    private int startHour = 8;
    /** 可预约的结束小时（24小时制）。 */
    private int endHour = 20;
    /** 支付超时时间（分钟）。 */
    private int paymentTimeoutMinutes = 15;
    /** 可提前核销的时间（分钟）。 */
    private int checkinAdvanceMinutes = 15;
    /** 超过预约开始时间多久后标记为爽约（分钟）。 */
    private int noshowGraceMinutes = 30;
}
