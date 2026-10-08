package com.wjx.wjxaiagentprojectback.code;

import cn.hutool.json.JSONUtil;
import com.wjx.wjxaiagentprojectback.ai.AiChatService;
import com.wjx.wjxaiagentprojectback.ai.AiChatServiceFactory;
import com.wjx.wjxaiagentprojectback.ai.model.FileCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.message.AiResponseMessage;
import com.wjx.wjxaiagentprojectback.ai.model.message.ToolExecutedMessage;
import com.wjx.wjxaiagentprojectback.ai.model.message.ToolRequestMessage;
import com.wjx.wjxaiagentprojectback.ai.model.parser.CodeParserExecutor;
import com.wjx.wjxaiagentprojectback.ai.model.saver.CodeFileSaverExecutor;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
@Slf4j
@Component
/**
 * 门面类
 * 具体的实现类
 * 代码托管工厂,根据不同的类型调用不同类型的文件保存方式。
 */
public class AiCodeGeneratorFacade {

    @Resource
    private AiChatService aiChatService;

    @Resource
    private AiChatServiceFactory aiChatServiceFactory;
    /**
     * 入门的“指导性的”工厂方式。
     *
     */
    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeTypeEnum codeGenTypeEnum,Long appId) {
        // 根据 appId 获取对应的 AI 服务实例
        AiChatService aiChatService = aiChatServiceFactory.getAiChatService(appId);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                //将类型和提示词给ai
                HtmlCodeResult result = aiChatService.htmlchat(1,userMessage);
                //保存器保存
                yield CodeFileSaverExecutor.executeSaver(result, CodeTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                FileCodeResult result = aiChatService.filechat(2,userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getCode();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeTypeEnum codeGenTypeEnum,Long appId) {
        // 根据 appId 获取对应的 AI 服务实例
        AiChatService aiChatService = aiChatServiceFactory.getAiChatService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiChatService.generateHtmlCodeStream(1,userMessage);
                yield processCodeStream(codeStream, CodeTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiChatService.generateMultiFileCodeStream(2,userMessage);
                yield processCodeStream(codeStream, CodeTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                // 注意：这里的第一个参数是 AiChatService.generateVueProjectCodeStream(@MemoryId long appId) 的 appId，
                // 它会被 FileWriteTool 的 @ToolMemoryId 接收，用来决定 Vue 工程落盘目录 vue_project_{appId}。
                // 必须传真实 appId（不能像 HTML/MULTI_FILE 那样传 1/2 的固定值），否则：
                //   1. 所有 Vue 工程都会写进同一个 vue_project_3 目录；
                //   2. 生成后的异步构建、静态预览、部署都按 vue_project_{真实appId} 找目录 → 找不到 → 站点打不开。
                TokenStream tokenStream = aiChatService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getCode();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeTypeEnum codeGenType,Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


}
