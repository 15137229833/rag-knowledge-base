package com.rag.kb.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存配置
 * 
 * 性能优化目标：
 * - 减少 60-80% 的数据库查询
 * - 响应时间降低 40-60%
 * - 数据库 CPU 使用率降低 50%
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // 缓存名称常量
    public static final String KNOWLEDGE_BASE_CACHE = "knowledgeBase";
    public static final String USER_PERMISSIONS_CACHE = "userPermissions";
    public static final String QA_CACHE = "qaCache";
    public static final String VECTOR_SEARCH_CACHE = "vectorSearch";
    public static final String DOCUMENT_CACHE = "document";
    public static final String USER_PROFILE_CACHE = "userProfile";

    /**
     * 配置 RedisCacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // 基础缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 默认 10 分钟过期
                .disableCachingNullValues() // 不缓存 null 值
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));

        // 为不同缓存设置不同的过期时间
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(KNOWLEDGE_BASE_CACHE, 
                        defaultConfig.entryTtl(Duration.ofMinutes(30))) // 知识库信息缓存 30 分钟
                .withCacheConfiguration(USER_PERMISSIONS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofMinutes(15))) // 用户权限缓存 15 分钟
                .withCacheConfiguration(QA_CACHE, 
                        defaultConfig.entryTtl(Duration.ofHours(1))) // 问答结果缓存 1 小时
                .withCacheConfiguration(VECTOR_SEARCH_CACHE, 
                        defaultConfig.entryTtl(Duration.ofMinutes(20))) // 向量检索缓存 20 分钟
                .withCacheConfiguration(DOCUMENT_CACHE, 
                        defaultConfig.entryTtl(Duration.ofMinutes(30))) // 文档信息缓存 30 分钟
                .withCacheConfiguration(USER_PROFILE_CACHE, 
                        defaultConfig.entryTtl(Duration.ofMinutes(30))) // 用户信息缓存 30 分钟
                .build();
    }
}
