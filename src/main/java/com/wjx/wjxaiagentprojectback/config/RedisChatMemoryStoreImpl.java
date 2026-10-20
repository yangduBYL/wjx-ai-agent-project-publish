package com.wjx.wjxaiagentprojectback.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 为 AI 应用的对话历史提供基于 Redis 的持久化存储，并通过动态随机 TTL 机制防止缓存击穿
 */
@Component
public class RedisChatMemoryStoreImpl implements ChatMemoryStore {
    @Value("${spring.data.redis.ttl:3600}")
    private long ttlSeconds;

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "chat:memory:";

    public RedisChatMemoryStoreImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = KEY_PREFIX + memoryId;
        String json = ChatMessageSerializer.messagesToJson(messages);
        // Set TTL (time to live) for the key in seconds,设置差异化时间
        // 核心改动：计算 TTL
        Duration dynamicTtl = calculateTtl();
        redisTemplate.opsForValue().set(key, json, dynamicTtl);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(KEY_PREFIX + memoryId);
    }

    /**
     * 叠加随机偏移量
     */
    private Duration calculateTtl() {
        // 策略2：叠加随机偏移量，防止同一时刻大量 Key 集体过期（缓存击穿）
        // 在基础 ttlSeconds 上叠加 0~300秒 的随机值
        long randomOffset = ThreadLocalRandom.current().nextLong(300);
        return Duration.ofSeconds(ttlSeconds + randomOffset);
    }
}