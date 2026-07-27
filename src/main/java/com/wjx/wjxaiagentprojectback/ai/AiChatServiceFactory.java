package com.wjx.wjxaiagentprojectback.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ai实体类创建工厂
 */
@Configuration
public class AiChatServiceFactory {
    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel; // 新增注入
    @Bean
    public AiChatService getAiChatService(){
        return AiServices.builder(AiChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel) // 必须传入流式模型
                .build();
    }
}
