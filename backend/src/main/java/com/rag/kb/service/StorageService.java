package com.rag.kb.service;

import com.rag.kb.config.AppProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final AppProperties appProperties;

    public void putObject(String objectKey, byte[] bytes, String contentType) {
        try {
            String ct = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(appProperties.getMinio().getBucket())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(ct)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储写入失败: " + e.getMessage(), e);
        }
    }

    public byte[] getObjectBytes(String objectKey) {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(appProperties.getMinio().getBucket())
                        .object(objectKey)
                        .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("对象存储读取失败: " + e.getMessage(), e);
        }
    }

    public void removeObject(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(appProperties.getMinio().getBucket())
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储删除失败: " + e.getMessage(), e);
        }
    }
}
