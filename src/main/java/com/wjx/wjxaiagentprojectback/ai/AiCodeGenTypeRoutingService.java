package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.ai.model.CodeTypeRoutingResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 代码生成类型路由服务（AI 自动选型）。
 * <p>
 * 作用：用户只给一句需求时，先让模型判断该走哪种生成方式（HTML / MULTI_FILE / VUE_PROJECT），
 * 再去调用对应的生成链，避免把类型写死。
 * <p>
 * 注意返回类型是 {@link CodeTypeRoutingResult}（单个字段的 JSON 对象）而不是 {@code CodeTypeEnum}：
 * 本项目对话模型配了 {@code response-format: json_object}，json_object 模式下模型只能输出 JSON 对象，
 * 与「返回枚举（模型输出裸值）」相冲突（实测 400）。转枚举用 {@link CodeTypeRoutingResult#toCodeType()}。
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求返回最合适的代码生成类型
     *
     * @param userMessage 用户需求描述
     * @return 包含 codeGenType 字段的路由结果（HTML / MULTI_FILE / VUE_PROJECT）
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeTypeRoutingResult routeCodeGenType(@UserMessage String userMessage);
}
