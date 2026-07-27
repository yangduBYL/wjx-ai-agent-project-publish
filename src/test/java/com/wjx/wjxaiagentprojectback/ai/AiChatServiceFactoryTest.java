package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiChatServiceFactoryTest {
    @Resource
    private AiChatService aiChatService;

    @Test
    void getAiChatService() {
    }


}