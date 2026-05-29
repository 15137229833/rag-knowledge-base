package com.rag.kb.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioBucketInitializer {

    private final MinioClient minioClient;
    private final AppProperties appProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        String bucket = appProperties.getMinio().getBucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed (is MinIO running?): {}", e.getMessage());
        }
    }
}
