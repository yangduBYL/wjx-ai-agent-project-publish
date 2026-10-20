package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFacade;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 服务工厂测试。
 * <p>
 * 注意：重构后 {@code AiChatService} 不再是 Spring 单例 bean（原来的 @Bean 已注释掉，
 * 改为 AiChatServiceFactory 按 appId + 类型现建），所以这里不能再 {@code @Resource AiChatService}，
 * 必须通过工厂拿服务，否则整个 Spring 上下文都会因为找不到 bean 而起不来。
 * <p>
 * 会真实调用大模型的用例默认关闭，需要时加 {@code -Dai.e2e=true}。
 */
@SpringBootTest
@DisplayName("AiChatServiceFactory：服务获取与对话记忆")
class AiChatServiceFactoryTest {

    @Resource
    private AiChatServiceFactory aiChatServiceFactory;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    @DisplayName("不同 appId / 不同类型各自拿到独立的服务实例，同一 key 命中缓存")
    void serviceInstanceIsPerAppIdAndType() {
        AiChatService html1 = aiChatServiceFactory.getAiChatService(1L, CodeTypeEnum.HTML);
        AiChatService html1Again = aiChatServiceFactory.getAiChatService(1L, CodeTypeEnum.HTML);
        AiChatService html2 = aiChatServiceFactory.getAiChatService(2L, CodeTypeEnum.HTML);
        AiChatService vue1 = aiChatServiceFactory.getAiChatService(1L, CodeTypeEnum.VUE_PROJECT);

        Assertions.assertSame(html1, html1Again, "同一个 appId + 类型应当命中缓存");
        Assertions.assertNotSame(html1, html2, "不同 appId 必须是不同实例（对话记忆隔离）");
        Assertions.assertNotSame(html1, vue1, "同一 appId 的不同生成类型也应当是不同实例");
    }

    @Test
    @DisplayName("兼容入口 getAiCodeGeneratorService 等价于 HTML 类型")
    void legacyEntryPointUsesHtmlType() {
        Assertions.assertSame(
                aiChatServiceFactory.getAiChatService(3L, CodeTypeEnum.HTML),
                aiChatServiceFactory.getAiCodeGeneratorService(3L));
    }

    @Test
    @DisplayName("对话记忆：同一个 memoryId 的上下文能连续对话（需 -Dai.e2e=true）")
    @EnabledIfSystemProperty(named = "ai.e2e", matches = "true")
    void chatMemoryKeepsContext() {
        AiChatService service = aiChatServiceFactory.getAiChatService(1L, CodeTypeEnum.HTML);
        Assertions.assertNotNull(service.htmlchat(1L, "做个程序员鱼皮的工具网站，总代码量不超过 20 行"));
        Assertions.assertNotNull(service.htmlchat(1L, "不要生成网站，告诉我你刚刚做了什么？"));
    }

    @Test
    @DisplayName("Vue 流式生成端到端（需 -Dai.e2e=true）")
    @EnabledIfSystemProperty(named = "ai.e2e", matches = "true")
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeTypeEnum.VUE_PROJECT, 1L);
        List<String> result = codeStream.collectList().block();
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
    }
}
