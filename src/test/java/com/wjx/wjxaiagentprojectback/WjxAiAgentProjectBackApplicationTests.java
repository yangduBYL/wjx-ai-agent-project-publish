package com.wjx.wjxaiagentprojectback;

import com.wjx.wjxaiagentprojectback.ai.AiChatService;
import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFactory;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

@Slf4j
@SpringBootTest
class WjxAiAgentProjectBackApplicationTests {
    @Resource
    private AiChatService aiChatService;

    @Resource
    private AiCodeGeneratorFactory aiCodeGeneratorFactory;

    @Test
    void contextLoads() {
        String results=aiChatService.webchat("像素风格的小游戏");
        //断言
        Assertions.assertNotNull(results);
    }

    @Test
    void test1(){
        HtmlCodeResult results=aiChatService.htmlchat(1,"跑酷类型的小游戏");
        log.info(String.valueOf(results));

        //断言
        Assertions.assertNotNull(results);
    }


    @Test
    void test3(){
        Long appId=3L;
        File fileresult = aiCodeGeneratorFactory.generateAndSaveCode("制作一个闯关打怪兽类型的小游戏", CodeTypeEnum.HTML,appId);
        Assertions.assertNotNull(fileresult);
    }
    @Test
    void generateAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFactory.generateAndSaveCodeStream("帮我制作一个任务记录网站",CodeTypeEnum.MULTI_FILE,2L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFactory.generateAndSaveCode("任务记录网站", CodeTypeEnum.MULTI_FILE, 9L);
        Assertions.assertNotNull(file);
    }


}
