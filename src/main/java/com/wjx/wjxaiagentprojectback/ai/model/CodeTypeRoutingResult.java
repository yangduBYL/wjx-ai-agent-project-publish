package com.wjx.wjxaiagentprojectback.ai.model;

import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;

/**
 * 代码生成类型路由结果。
 * <p>
 * 为什么不直接返回 {@link CodeTypeEnum}：本项目的对话模型（langchain4j.open-ai.chat-model）
 * 配置了 {@code response-format: json_object}，DeepSeek 在 json_object 模式下**只接受 JSON 对象**，
 * 而 langchain4j 对「返回枚举」只会追加一句 “answer strictly with one of these enums: ...”，
 * 期望模型直接输出裸值（如 {@code HTML}）——两者冲突，实测会直接 400：
 * “Prompt must contain the word 'json' in some form to use 'response_format' of type 'json_object'”。
 * <p>
 * 所以这里用一个只有单个字段的 JSON 对象承接，再由 {@link #toCodeType()} 转成枚举。
 *
 * @param codeGenType 模型返回的代码生成类型字符串（HTML / MULTI_FILE / VUE_PROJECT）
 */
public record CodeTypeRoutingResult(String codeGenType) {

    /**
     * 转成枚举（模型返回了未知值时返回 null，由调用方决定兜底策略）
     * <p>
     * 注意：不能直接用 {@code CodeTypeEnum.getValueCode(code)}，它匹配的是「小写 code」（vue_project），
     * 而路由提示词里给模型的候选值是枚举名（VUE_PROJECT）。这里两种写法都兼容，另外兼容大小写。
     *
     * @return 代码生成类型枚举
     */
    public CodeTypeEnum toCodeType() {
        if (codeGenType == null) {
            return null;
        }
        String value = codeGenType.trim();
        for (CodeTypeEnum type : CodeTypeEnum.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getCode().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 是否是一个可识别的类型
     */
    public boolean isValid() {
        return toCodeType() != null;
    }
}
