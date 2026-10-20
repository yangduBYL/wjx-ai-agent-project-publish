package com.wjx.wjxaiagentprojectback.core.handle;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wjx.wjxaiagentprojectback.ai.model.message.*;
import com.wjx.wjxaiagentprojectback.ai.tools.BaseTool;
import com.wjx.wjxaiagentprojectback.ai.tools.ToolManager;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import com.wjx.wjxaiagentprojectback.core.builder.VueProjectBuilder;
import com.wjx.wjxaiagentprojectback.core.report.VueProjectReportBuilder;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.ChatHistoryMessageTypeEnum;
import com.wjx.wjxaiagentprojectback.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息。
 * <p>
 * Vue 模式一次写入十几个文件、每个文件几十到几百行，源码不再回显到对话里
 * （刷屏、落盘体积大、与右侧预览重复），改为：
 * 1. 生成过程中逐行回显「已写入哪些文件」（纯文本，不含源码）；
 * 2. 生成结束后追加一份「文件结构层级 + 每个文件说明」的报告，前端渲染成「文件结构」面板。
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {
    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolManager toolManager;


    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        // 本次生成的文件写入记录：每次生成都要新建实例（不能做成字段，否则并发会话会串数据）
        VueProjectReportBuilder reportBuilder = new VueProjectReportBuilder(appId);
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, reportBuilder, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                // 流式响应全部结束后，追加「文件结构层级 + 文件说明」报告（Vue 模式不回显源码）
                .concatWith(Flux.defer(() -> {
                    String report = reportBuilder.buildReport();
                    if (StrUtil.isBlank(report)) {
                        // 本次没有写入任何文件（例如 AI 只做了追问），不追加报告
                        return Flux.empty();
                    }
                    chatHistoryStringBuilder.append(report);
                    return Flux.just(report);
                }))
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk,
                                          VueProjectReportBuilder reportBuilder,
                                          StringBuilder chatHistoryStringBuilder,
                                          Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String result = StrUtil.nullToEmpty(toolExecutedMessage.getResult());
                String output;
                if (VueProjectReportBuilder.isFileWriteResult(result)) {
                    // 文件写入：路径优先从「工具返回值」里解析 —— 返回值里就带真实相对路径，
                    // 不依赖流式工具参数（参数在消息组装阶段可能取不到，曾经因此显示成 null）；
                    // 再依次兜底：工具参数 -> 序号占位，保证用户可见文案里永远不出现 null
                    String relativePath = VueProjectReportBuilder.parsePathFromResult(result);
                    if (StrUtil.isBlank(relativePath)) {
                        relativePath = parsePathFromArguments(toolExecutedMessage.getArguments());
                    }
                    if (StrUtil.isBlank(relativePath)) {
                        relativePath = reportBuilder.nextFallbackName();
                    }
                    boolean success = result.trim().startsWith("文件写入成功");
                    // recordWrite 一边回显「✅ 已写入 xxx · N 行 · 大小」，一边把文件记进报告，
                    // 生成结束后由 buildReport() 汇总成「文件结构」面板
                    output = reportBuilder.recordWrite(relativePath, success, result);
                } else {
                    // 读取/修改/删除/读目录等工具：回显「工具名 + 关键参数」，参数取不到时用占位文本
                    BaseTool tool = toolManager.getTool(toolExecutedMessage.getName());
                    String display = tool == null
                            ? String.format("[工具调用] %s", StrUtil.blankToDefault(toolExecutedMessage.getName(), "未知工具"))
                            : tool.generateToolExecutedResult(parseArguments(toolExecutedMessage.getArguments()));
                    output = String.format("\n\n%s\n\n", display);
                }
                // 输出前端和要持久化的内容
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    /**
     * 从工具调用参数里解析文件相对路径（解析不出来返回 null）
     */
    private String parsePathFromArguments(String arguments) {
        try {
            JSONObject jsonObject = JSONUtil.parseObj(StrUtil.blankToDefault(arguments, "{}"));
            return jsonObject.getStr("relativeFilePath");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全解析工具参数：空串 / 非法 JSON 时返回空对象，避免展示时 NPE
     */
    private JSONObject parseArguments(String arguments) {
        try {
            return JSONUtil.parseObj(StrUtil.blankToDefault(arguments, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
