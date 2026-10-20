package com.wjx.wjxaiagentprojectback.ai.tools.toolsName;

import cn.hutool.json.JSONObject;
import com.wjx.wjxaiagentprojectback.ai.tools.BaseTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件读取工具
 * 支持 AI 通过工具调用的方式读取文件内容
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool {

    @Tool("读取工程内某个文件的完整内容，用于修改前先了解现有代码")
    public String readFile(
            @P("文件的相对路径，例如 src/main.js")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = resolveProjectPath(relativeFilePath, appId);
            if (!Files.isRegularFile(path)) {
                return "文件不存在: " + relativeFilePath;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            // 超大文件只回传前 MAX_READ_CHARS 个字符，避免撑爆模型上下文
            if (content.length() > MAX_READ_CHARS) {
                return content.substring(0, MAX_READ_CHARS) + "\n...（文件过长，已截断，共 " + content.length() + " 字符）";
            }
            return content;
        } catch (IOException e) {
            String errorMessage = "文件读取失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        } catch (IllegalArgumentException e) {
            log.warn("文件读取失败: {}", e.getMessage());
            return "文件读取失败: " + relativeFilePath + ", 错误: " + e.getMessage();
        }
    }

    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return String.format("[工具调用] %s %s", getDisplayName(), readArg(arguments, "relativeFilePath"));
    }
}
