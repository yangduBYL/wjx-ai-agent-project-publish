package com.wjx.wjxaiagentprojectback.ai.tools.toolsName;

import cn.hutool.json.JSONObject;
import com.wjx.wjxaiagentprojectback.ai.tools.BaseTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件删除工具
 * 支持 AI 通过工具调用的方式删除文件
 */
@Slf4j
@Component
public class FileDeleteTool extends BaseTool {

    @Tool("删除工程内的文件或目录（目录会递归删除），用于删掉不再需要的文件")
    public String deleteFile(
            @P("要删除的文件或目录的相对路径，例如 src/pages/Old.vue")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = resolveProjectPath(relativeFilePath, appId);
            if (!Files.exists(path)) {
                return "文件不存在，无需删除: " + relativeFilePath;
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    // 先删子文件再删目录
                    for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            } else {
                Files.delete(path);
            }
            log.info("成功删除: {}", path.toAbsolutePath());
            return "文件删除成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件删除失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        } catch (IllegalArgumentException e) {
            log.warn("文件删除失败: {}", e.getMessage());
            return "文件删除失败: " + relativeFilePath + ", 错误: " + e.getMessage();
        }
    }

    @Override
    public String getToolName() {
        return "deleteFile";
    }

    @Override
    public String getDisplayName() {
        return "删除文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return String.format("[工具调用] %s %s", getDisplayName(), readArg(arguments, "relativeFilePath"));
    }
}
