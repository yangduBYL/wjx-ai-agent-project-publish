package com.wjx.wjxaiagentprojectback.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisCacheManagerConfig {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public CacheManager cacheManager() {
        // 注意：这里必须“自己 new ObjectMapper + 手动开启 default typing”。
        // 原因：new GenericJackson2JsonRedisSerializer(objectMapper) 传入自定义 mapper 时，
        // 它不会帮你开启 default typing，序列化出来的 JSON 不带类型信息，
        // 缓存命中反序列化只会得到 LinkedHashMap，返回给前端时会 ClassCastException。
        ObjectMapper objectMapper = new ObjectMapper();
        // 1.支持 Java8 时间类型（LocalDateTime 等）
        objectMapper.registerModule(new JavaTimeModule());
        // 2.时间序列化成 ISO-8601 字符串（redis-cli 里直接可读）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 3.写入类型信息（@class 属性），保证反序列化能还原成原始类型（BaseResponse / Page / App_UserVo）
        //   安全性说明：LaissezFaireSubTypeValidator 不做类型白名单校验，
        //   只适合本地/可信 Redis；生产环境应换成限制包名的 PolymorphicTypeValidator。
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        // value 使用 JSON 序列化器（支持复杂对象，且缓存内容肉眼可读）
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // 默认配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 默认 30 分钟过期
                .disableCachingNullValues() // 禁用 null 值缓存
                // key 使用 String 序列化器
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // value 使用上面配置好的 JSON 序列化器
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                // 针对 good_app_page 配置5分钟过期
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}
