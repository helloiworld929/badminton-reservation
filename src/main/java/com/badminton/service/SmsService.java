package com.badminton.service;

import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.badminton.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// 短信服务：通过阿里云发送验证码，验证码存入 Redis 并支持频率限制
@Service
public class SmsService {
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String REDIS_PREFIX = "sms:code:";

    @Value("${app.sms.code-length:6}")
    private int codeLength;

    @Value("${app.sms.code-expire-seconds:300}")
    private long codeExpireSeconds;

    @Value("${app.sms.sign-name:羽悦羽毛球}")
    private String signName;

    @Value("${app.sms.template-code:SMS_123456789}")
    private String templateCode;

    private final StringRedisTemplate stringRedisTemplate;
    private final AsyncClient smsClient;

    public SmsService(StringRedisTemplate stringRedisTemplate, AsyncClient smsClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.smsClient = smsClient;
    }

    /**
     * 发送验证码：限频60s -> 存Redis -> 调阿里云API；发送失败则回删Redis中的验证码
     */
    public void sendCode(String phone) {
        // 频率限制：同一手机号每 60 秒最多发送一次
        String rateKey = REDIS_PREFIX + "rate:" + phone;
        Boolean canSend = stringRedisTemplate.opsForValue()
                .setIfAbsent(rateKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(canSend)) {
            throw new BusinessException("验证码已发送，请60秒后重试");
        }

        String code = generateCode();
        String redisKey = REDIS_PREFIX + phone;

        // 先将验证码存入 Redis
        stringRedisTemplate.opsForValue().set(redisKey, code, codeExpireSeconds, TimeUnit.SECONDS);

        // 调用阿里云短信 API
        try {
            sendSms(phone, code);
            log.info("短信已发送至 {}", phone);
        } catch (Exception e) {
            // 短信发送失败时，删除 Redis 中的验证码
            stringRedisTemplate.delete(redisKey);
            log.error("SMS send failed to {}: {}", phone, e.getMessage());
            throw new BusinessException("短信发送失败，请稍后重试");
        }
    }

    /**
     * 校验验证码：888888 为万能开发码，跳过 Redis 验证
     */
    public void validateCode(String phone, String code) {
        if (isDevCode(code)) {
            return;
        }

        String redisKey = REDIS_PREFIX + phone;
        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!storedCode.equals(code)) {
            throw new BusinessException("验证码错误");
        }

        // 验证成功后删除，防止同一验证码被重复使用
        stringRedisTemplate.delete(redisKey);
    }

    /**
     * 调用阿里云短信 API 发送验证码（复用单例 AsyncClient）。
     */
    private void sendSms(String phone, String code) throws Exception {
        SendSmsVerifyCodeRequest request = SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .signName(signName)
                .templateCode(templateCode)
                .templateParam("{\"code\":\"" + code + "\",\"min\":\"" + (codeExpireSeconds / 60) + "\"}")
                .build();

        CompletableFuture<SendSmsVerifyCodeResponse> response = smsClient.sendSmsVerifyCode(request);
        SendSmsVerifyCodeResponse resp = response.get();
        String respCode = resp.getBody() != null ? resp.getBody().getCode() : null;
        log.info("SMS API response: code={}, message={}, requestId={}",
                respCode,
                resp.getBody() != null ? resp.getBody().getMessage() : "null",
                resp.getBody() != null ? resp.getBody().getRequestId() : "null");

        if (!"OK".equals(respCode)) {
            throw new RuntimeException("SMS API returned error: code=" + respCode
                    + ", message=" + (resp.getBody() != null ? resp.getBody().getMessage() : "null"));
        }
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        int code = ThreadLocalRandom.current().nextInt(bound);
        return String.format("%0" + codeLength + "d", code);
    }

    // 万能验证码 888888，仅开发/测试使用
    private boolean isDevCode(String code) {
        return "888888".equals(code);
    }
}
