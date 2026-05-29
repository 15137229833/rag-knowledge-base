package com.rag.kb.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final AppProperties appProperties;

    @Bean
    public MinioClient minioClient() {
        AppProperties.Minio m = appProperties.getMinio();
        return MinioClient.builder()
                .endpoint(m.getEndpoint())
                .credentials(m.getAccessKey(), m.getSecretKey())
                .build();
    }
}
