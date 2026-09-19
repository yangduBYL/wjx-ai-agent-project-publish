package com.wjx.wjxaiagentprojectback.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
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

    @Resource
    private ChatMemoryStore chatMemoryStore;
    @Bean
    public AiChatService getAiChatService(){
        return AiServices.builder(AiChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel) // 必须传入流式模型
                //根据id构建独立的对话记忆
                .chatMemoryProvider(memoryId-> MessageWindowChatMemory
                        .builder()
                        .id(memoryId)
                        .chatMemoryStore(chatMemoryStore)
                        .maxMessages(20)//最大消息数上下文
                        .build())
                .build();
    }
}
