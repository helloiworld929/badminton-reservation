package com.badminton.config;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import darabonba.core.client.ClientOverrideConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 SDK 客户端单例配置，避免每次调用都新建连接。
 */
@Configuration
public class AliyunConfig {

    @Value("${app.sms.access-key-id}")
    private String accessKeyId;

    @Value("${app.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${app.oss.endpoint}")
    private String ossEndpoint;

    // ===== SMS AsyncClient（单例，Spring 容器关闭时自动调 close） =====
    @Bean(destroyMethod = "close")
    public AsyncClient smsAsyncClient() {
        Credential credential = Credential.builder()
                .accessKeyId(accessKeyId)
                .accessKeySecret(accessKeySecret)
                .build();
        StaticCredentialProvider provider = StaticCredentialProvider.create(credential);

        return AsyncClient.builder()
                .region("cn-hangzhou")
                .credentialsProvider(provider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride("dypnsapi.aliyuncs.com"))
                .build();
    }

    // ===== OSS Client（单例，销毁时调 shutdown） =====
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        return new OSSClientBuilder().build(ossEndpoint, accessKeyId, accessKeySecret);
    }
}
