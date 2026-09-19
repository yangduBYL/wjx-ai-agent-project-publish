package com.wjx.wjxaiagentprojectback.code;

import com.wjx.wjxaiagentprojectback.ai.AiChatService;
import com.wjx.wjxaiagentprojectback.ai.model.FileCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.parser.CodeParserExecutor;
import com.wjx.wjxaiagentprojectback.ai.model.saver.CodeFileSaverExecutor;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
@Slf4j
@Component
/**
 * 代码托管工厂,根据不同的类型调用不同类型的文件保存方式。
 */
public class AiCodeGeneratorFactory {

    @Resource
    private AiChatService aiChatService;
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
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiChatService.generateHtmlCodeStream(1,userMessage);
                yield processCodeStream(codeStream, CodeTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiChatService.generateMultiFileCodeStream(1,userMessage);
                yield processCodeStream(codeStream, CodeTypeEnum.MULTI_FILE,appId);
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
}
