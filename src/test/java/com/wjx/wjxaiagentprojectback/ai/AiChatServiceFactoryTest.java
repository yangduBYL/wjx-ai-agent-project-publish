package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;

import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFacade;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

@SpringBootTest
class AiChatServiceFactoryTest {
    @Resource
    private AiChatService aiChatService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

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
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeTypeEnum.VUE_PROJECT, 1L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }



}