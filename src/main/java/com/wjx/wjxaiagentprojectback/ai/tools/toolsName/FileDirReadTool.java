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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文件目录读取工具
 * 让 AI 先看清工程目录结构，再做修改
 */
@Slf4j
@Component
public class FileDirReadTool extends BaseTool {

    /** 这些目录不参与展示：依赖产物，动辄几万个文件，且与源码无关 */
    private static final Set<String> IGNORED_DIRS =
            Set.of("node_modules", "dist", ".git", "target", ".idea", ".vite");

    @Tool("读取工程目录结构（目录树），用于修改前了解当前项目有哪些文件")
    public String readDir(
            @P("要读取的相对目录路径，根目录传空字符串或不传")
            String relativeDirPath,
            @ToolMemoryId Long appId
    ) {
        try {
            // 与 langchain4j 工具调用约定一致：目录路径可省略
            Path dir = StrUtil.isBlank(relativeDirPath)
                    ? projectRoot(appId)
                    : resolveProjectPath(relativeDirPath, appId);
            if (!Files.exists(dir)) {
                return "目录不存在: " + relativeDirPath;
            }
            Path root = projectRoot(appId).normalize();
            List<String> lines = new ArrayList<>();
            lines.add("工程目录结构（vue_project_" + appId + "）:");
            // 用 walkFileTree 主动剪枝：node_modules / dist 等目录直接跳过，避免白遍历几万个文件
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes attrs) {
                    if (!dirPath.equals(dir) && IGNORED_DIRS.contains(dirPath.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (lines.size() < MAX_DIR_ENTRIES) {
                        lines.add(relative(root, dirPath));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
                    if (lines.size() < MAX_DIR_ENTRIES) {
                        lines.add(relative(root, filePath));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            lines.add("（最多展示 " + MAX_DIR_ENTRIES + " 条，已忽略 node_modules/dist/.git 等目录）");
            return String.join("\n", lines);
        } catch (IOException e) {
            String errorMessage = "目录读取失败: " + relativeDirPath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        } catch (IllegalArgumentException e) {
            log.warn("目录读取失败: {}", e.getMessage());
            return "目录读取失败: " + relativeDirPath + ", 错误: " + e.getMessage();
        }
    }

    /**
     * 判断某个路径是否落在被忽略的目录里
     */
    private boolean ignored(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 输出「./相对路径」，目录追加 "/"
     */
    private String relative(Path root, Path path) {
        String rel = root.relativize(path).toString().replace('\\', '/');
        if (StrUtil.isBlank(rel)) {
            return "./";
        }
        return "./" + rel + (Files.isDirectory(path) ? "/" : "");
    }

    @Override
    public String getToolName() {
        return "readDir";
    }

    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = arguments == null ? null : arguments.getStr("relativeDirPath");
        if (StrUtil.isEmpty(relativeDirPath)) {
            relativeDirPath = "根目录";
        }
        return String.format("[工具调用] %s %s", getDisplayName(), relativeDirPath);
    }
}
