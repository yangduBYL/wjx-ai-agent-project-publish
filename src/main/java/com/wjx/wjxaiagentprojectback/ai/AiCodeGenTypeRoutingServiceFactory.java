package com.wjx.wjxaiagentprojectback.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 代码生成类型路由服务工厂。
 * <p>
 * 关键点：{@code @Scope("prototype")} —— 每次 {@link #createAiCodeGenTypeRoutingService()} 都返回一个**新的**
 * AiService 实例（Spring 的 @Configuration 代理会拦截方法调用），这样并发的多个请求各自持有独立的调用上下文，
 * 不会共用同一份路由服务状态。
 * <p>
 * 模型这里用的是默认对话模型（分类任务，便宜且快），不需要推理模型；
 * langchain4j 1.x 的 ChatModel 实现本身是无每请求可变状态的，可以安全共享。
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    /**
     * 创建路由服务（多例）
     *
     * @return 每次调用都是新的实例
     */
    @Bean
    @Scope("prototype")
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        log.debug("创建新的代码生成类型路由服务实例");
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }
}
