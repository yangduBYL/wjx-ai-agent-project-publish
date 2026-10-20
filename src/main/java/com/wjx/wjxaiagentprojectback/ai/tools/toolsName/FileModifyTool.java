package com.wjx.wjxaiagentprojectback.ai.tools.toolsName;

import cn.hutool.core.util.StrUtil;
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
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容（局部替换，不重写整个文件）
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    @Tool("修改工程内已有文件的部分内容：把文件里出现的 oldContent 精确替换为 newContent（只替换第一处）")
    public String modifyFile(
            @P("文件的相对路径，例如 src/pages/Home.vue")
            String relativeFilePath,
            @P("被替换的原内容，必须与文件中的内容完全一致（含缩进）")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        try {
            if (StrUtil.isEmpty(oldContent)) {
                return "文件修改失败: oldContent 不能为空，若想整体重写请使用 writeFile 工具";
            }
            Path path = resolveProjectPath(relativeFilePath, appId);
            if (!Files.isRegularFile(path)) {
                return "文件不存在: " + relativeFilePath + "，若想新建文件请使用 writeFile 工具";
            }
            String origin = Files.readString(path, StandardCharsets.UTF_8);
            int index = origin.indexOf(oldContent);
            if (index < 0) {
                return "文件修改失败: 在 " + relativeFilePath + " 中找不到要替换的原内容，"
                        + "请先用 readFile 工具确认文件当前内容后再修改";
            }
            String updated = origin.substring(0, index)
                    + (newContent == null ? "" : newContent)
                    + origin.substring(index + oldContent.length());
            Files.writeString(path, updated, StandardCharsets.UTF_8);
            log.info("成功修改文件: {}", path.toAbsolutePath());
            return "文件修改成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件修改失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        } catch (IllegalArgumentException e) {
            log.warn("文件修改失败: {}", e.getMessage());
            return "文件修改失败: " + relativeFilePath + ", 错误: " + e.getMessage();
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 同样只回显文件路径：替换前后的大段代码不进对话历史
        return String.format("[工具调用] %s %s", getDisplayName(), readArg(arguments, "relativeFilePath"));
    }
}
