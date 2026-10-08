package com.wjx.wjxaiagentprojectback.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wjx.wjxaiagentprojectback.ai.tools.FileWriteTool;
import com.wjx.wjxaiagentprojectback.config.ReasoningStreamingChatModelConfig;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.service.ChatHistoryService;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ai实体类创建工厂
 */
@Slf4j
@Configuration
public class AiChatServiceFactory {
    @Resource
    private ChatModel chatModel;

    //当引入相同的流式模型时，通过知名官方的bean名称，可规避系统的多个bean报错。
    @Resource
    private StreamingChatModel openAiStreamingChatModel; // 新增注入

    @Resource
    private StreamingChatModel reasoningStreamingChatModel; // 新增注入

    @Resource
    private ChatMemoryStore chatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;


    @Bean
    public AiChatService getAiChatService(){
        return AiServices.builder(AiChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(openAiStreamingChatModel) // 必须传入流式模型
                //根据id构建独立的对话记忆
                .chatMemoryProvider(memoryId-> MessageWindowChatMemory
                        .builder()
                        .id(memoryId)
                        .chatMemoryStore(chatMemoryStore)
                        .maxMessages(20)//最大消息数上下文
                        .build())
                .build();
    }

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     *                  Long,AiChatService
     */
    private final Cache<Long, AiChatService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiChatService getAiChatService(long appId) {
        return serviceCache.get(appId, this::createAiChatService);
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiChatService createAiChatService(long appId) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(20)
                .build();
        // AiChatServiceFactory.java:119-122
        return AiServices.builder(AiChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(openAiStreamingChatModel)
                .chatMemoryProvider(memoryId -> chatMemory) // 传入了 chatMemoryProvider
                .build();

    }



    /**
     * 创建新的 AI 服务实例
     */

    private AiChatService createAiChatService(long appId, CodeTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue 项目生成使用推理模型
            case VUE_PROJECT -> AiServices.builder(AiChatService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(new FileWriteTool())
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    .build();
            // HTML 和多文件生成使用默认模型
            // 注意：这里必须用 chatMemoryProvider，不能改成 chatMemory(chatMemory)。
            // 原因（langchain4j 1.1.0）：AiChatService 的 generateHtmlCodeStream / generateMultiFileCodeStream
            // 带有 @MemoryId 参数，门面类传的是 1 / 2（非 "default"）。若只配置 chatMemory，
            // DefaultAiServices 会用 ChatMemoryService(ChatMemory) 这个构造器：它只给 defaultChatMemory 赋值，
            // chatMemories 与 chatMemoryProvider 均为 null；随后 invoke 走到
            // ChatMemoryService.getOrCreateChatMemory(1) 的第 38 行
            // （chatMemories.computeIfAbsent(memoryId, chatMemoryProvider::get)），
            // 方法引用 chatMemoryProvider::get 的隐含空检查直接抛 NullPointerException，
            // 所以只要 @MemoryId 与 chatMemory() 同时出现就必崩（这就是之前 chat/gen/code 报 NPE 的原因）。
            case HTML, MULTI_FILE -> AiServices.builder(AiChatService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getCode());
        };
    }

    /**
     * AI 服务实例缓存
     */
    private final Cache<String, AiChatService> serviceCache2 = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）这个方法是为了兼容历史逻辑
     */
    public AiChatService getAiCodeGeneratorService(long appId) {
        return getAiChatService(appId, CodeTypeEnum.HTML);
    }

    /**
     * 根据 appId 和代码生成类型获取服务（带缓存）
     */
    public AiChatService getAiChatService(long appId, CodeTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache2.get(cacheKey, key -> createAiChatService(appId, codeGenType));
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getCode();
    }


}
