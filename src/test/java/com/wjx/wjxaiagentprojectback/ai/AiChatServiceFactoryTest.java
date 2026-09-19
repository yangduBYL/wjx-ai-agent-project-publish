package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiChatServiceFactoryTest {
    @Resource
    private AiChatService aiChatService;

    @Test
    void getAiChatService() {
    }

    @Test
    void testChatMemory() {
        HtmlCodeResult result = aiChatService.htmlchat(1, "做个程序员鱼皮的工具网站，总代码量不超过 20 行");
        Assertions.assertNotNull(result);
        result = aiChatService.htmlchat(1, "不要生成网站，告诉我你刚刚做了什么？");
        Assertions.assertNotNull(result);
        result = aiChatService.htmlchat(2, "做个程序员鱼皮的工具网站，总代码量不超过 20 行");
        Assertions.assertNotNull(result);
        result = aiChatService.htmlchat(2, "不要生成网站，告诉我你刚刚做了什么？");
        Assertions.assertNotNull(result);
    }
    @Test
    public void testRawJedisConnection() {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            jedis.auth("123456"); // 手动认证
            String pong = jedis.ping();
            System.out.println("Raw Jedis PING response: " + pong);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}