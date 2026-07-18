package com.badminton.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// 阿里云OSS文件上传：仅支持图片，限制5MB，按 category/日期/UUID.扩展名 组织路径
@Service
public class OssService {
    private static final Logger log = LoggerFactory.getLogger(OssService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Value("${app.oss.bucket-name}")
    private String bucketName;

    @Value("${app.oss.base-url}")
    private String baseUrl;

    private final OSS ossClient;

    public OssService(OSS ossClient) {
        this.ossClient = ossClient;
    }

    /**
     * 上传文件到 OSS，返回公开访问 URL（复用单例 OSS 客户端）。
     * 路径格式：{category}/yyyy/MM/dd/{uuid}.{ext}
     */
    public String upload(MultipartFile file, String category) throws IOException {
        validateFile(file);

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        String objectName = buildObjectName(category, extension);

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(bucketName, objectName, inputStream);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            request.setMetadata(metadata);
            ossClient.putObject(request);
        }

        String url = baseUrl + objectName;
        log.info("OSS 上传成功: {} -> {}", originalName, url);
        return url;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持上传图片文件");
        }

        // 单文件上限 5MB
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }
    }

    private String buildObjectName(String category, String extension) {
        String datePath = LocalDate.now().format(DATE_FMT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("%s/%s/%s.%s", category, datePath, uuid, extension);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        // jpeg/webp 统一归为 jpg，简化前端处理
        if ("jpeg".equals(ext)) return "jpg";
        if ("webp".equals(ext)) return "jpg";
        return ext.length() <= 4 ? ext : "jpg";
    }
}
