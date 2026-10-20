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
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 * <p>
 * 注意：这个 @Tool 方法就是「Vue 工程模式能否落盘」的命门。
 * 少了它，模型收到的工具列表是空的，AI 只会输出文字、不会创建任何文件，
 * 随后 VueProjectBuilder 就会报「项目目录不存在」。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    @Tool("写入文件到指定路径：文件不存在则创建，存在则完全覆盖。Vue 工程模式下路径一律相对工程根目录")
    public String writeFile(
            @P("文件的相对路径，例如 package.json、src/main.js、src/pages/Home.vue")
            String relativeFilePath,
            @P("要写入文件的完整内容，必须是可运行的完整代码，禁止省略或用占位注释")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = resolveProjectPath(relativeFilePath, appId);
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 显式用 UTF-8 写盘，避免中文内容乱码
            Files.writeString(path,
                    content == null ? "" : content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        } catch (IllegalArgumentException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn(errorMessage);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = readArg(arguments, "relativeFilePath");
        // 只回显「写了哪个文件」，不回显源码：Vue 模式一次写十几个文件，
        // 把源码贴进对话既刷屏又会把对话历史撑爆（文件清单由 VueProjectReportBuilder 汇总）
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
