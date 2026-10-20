package com.wjx.wjxaiagentprojectback.core.report;

import cn.hutool.core.util.StrUtil;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue 工程生成报告构建器。
 * <p>
 * Vue 模式一次会写入十几个文件、每个文件几十到几百行，源码不适合再回显进对话
 * （刷屏、落库体积大、与右侧预览重复），因此这里把每一次文件写入记录下来，
 * 最后汇总成「文件结构层级 + 每个文件说明 + 汇总信息」作为回复体。
 * <p>
 * 注意：每次生成都要 new 一个实例（不是单例 Bean），否则并发会话会串数据。
 *
 * @author wjx
 */
public class VueProjectReportBuilder {

    /**
     * 工具返回值前缀，与 {@code FileWriteTool} 的返回值保持一致。
     * 用返回值里的相对路径比用流式参数可靠：流式参数在消息组装时可能取不到（曾经因此显示成 null）。
     */
    private static final String WRITE_OK_PREFIX = "文件写入成功: ";
    private static final String WRITE_FAIL_PREFIX = "文件写入失败: ";
    /**
     * 失败原因在返回值里的分隔符
     */
    private static final String ERROR_SPLIT = ", 错误: ";

    /**
     * 常见工程文件的中文说明（未命中时按路径/后缀推断）
     */
    private static final Map<String, String> KNOWN_DESCRIPTIONS = Map.ofEntries(
            Map.entry("package.json", "项目依赖与运行脚本"),
            Map.entry("package-lock.json", "依赖版本锁定文件"),
            Map.entry("vite.config.js", "Vite 构建/开发配置"),
            Map.entry("vite.config.ts", "Vite 构建/开发配置"),
            Map.entry("index.html", "页面入口 HTML"),
            Map.entry("src/main.js", "应用入口（挂载根组件）"),
            Map.entry("src/main.ts", "应用入口（挂载根组件）"),
            Map.entry("src/App.vue", "根组件（页面框架）"),
            Map.entry("src/router/index.js", "路由配置（hash 模式）"),
            Map.entry("src/router/index.ts", "路由配置（hash 模式）"),
            Map.entry("src/styles/global.css", "全局样式"),
            Map.entry("src/data/mock.js", "模拟数据"),
            Map.entry("README.md", "说明文档")
    );

    private final long appId;

    /**
     * 按写入顺序记录；同一路径重复写入时保留首次的顺序、更新内容
     */
    private final Map<String, FileItem> files = new LinkedHashMap<>();

    /** 参数完全取不到时的占位序号 */
    private int fallbackSeq = 0;

    public VueProjectReportBuilder(long appId) {
        this.appId = appId;
    }

    /**
     * 参数完全取不到时用的占位名，保证用户可见文本里永远不会出现 null
     */
    public String nextFallbackName() {
        return "第 " + (++fallbackSeq) + " 个文件";
    }

    /**
     * 记录一次文件写入，返回给前端的进度行（纯文本，不含源码）
     *
     * @param relativePath 相对路径（从工具返回值里解析）
     * @param success      是否写入成功
     * @param rawResult    工具原始返回值
     * @return 进度行文本
     */
    public String recordWrite(String relativePath, boolean success, String rawResult) {
        String path = StrUtil.blankToDefault(relativePath, "未命名文件");
        if (success) {
            int lines = countLines(path);
            long bytes = fileSize(path);
            files.put(path, new FileItem(path, true, lines, bytes));
            return String.format("\n✅ 已写入 %s · %d 行 · %s\n", path, lines, humanSize(bytes));
        }
        String reason = StrUtil.nullToEmpty(rawResult);
        int idx = reason.indexOf(ERROR_SPLIT);
        if (idx >= 0) {
            reason = reason.substring(idx + ERROR_SPLIT.length());
        }
        files.put(path, new FileItem(path, false, 0, 0));
        return String.format("\n❌ %s 写入失败：%s\n", path, truncate(reason, 120));
    }

    /**
     * 生成最终报告：汇总信息 + 文件结构层级（用 tree 围栏包裹，前端渲染成「文件结构」面板）
     *
     * @return 报告文本；本次没有写入任何文件时返回空串
     */
    public String buildReport() {
        if (files.isEmpty()) {
            return "";
        }
        // 1. 组装层级
        Node root = new Node("");
        for (FileItem item : files.values()) {
            String[] parts = item.path().split("/");
            Node cursor = root;
            for (int i = 0; i < parts.length; i++) {
                Node child = cursor.children.computeIfAbsent(parts[i], Node::new);
                if (i == parts.length - 1) {
                    child.file = item;
                }
                cursor = child;
            }
        }
        // 2. 计算对齐列（文件名列、说明列）
        //    文件名列 = 缩进(4*深度) + 树线「├── 」(4) + 文件名宽度
        int nameColumn = 0;
        int descColumn = 0;
        for (Node node : collect(root, 1)) {
            if (node.file == null) {
                continue;
            }
            nameColumn = Math.max(nameColumn, (depth(node) * 4) + 4 + node.name.length());
            descColumn = Math.max(descColumn, displayWidth(describe(node.file.path())));
        }
        // 3. 渲染
        StringBuilder tree = new StringBuilder();
        renderChildren(root, "", tree, nameColumn, descColumn);
        // 4. 汇总
        long totalLines = files.values().stream().filter(FileItem::success).mapToLong(FileItem::lines).sum();
        long totalBytes = files.values().stream().filter(FileItem::success).mapToLong(FileItem::bytes).sum();
        long failedCount = files.values().stream().filter(item -> !item.success()).count();

        StringBuilder sb = new StringBuilder();
        sb.append("\n✅ 生成完成：共 ").append(files.size()).append(" 个文件");
        if (failedCount > 0) {
            sb.append("（失败 ").append(failedCount).append(" 个）");
        }
        sb.append(" · 合计 ").append(totalLines).append(" 行 · ").append(humanSize(totalBytes)).append('\n');
        sb.append("📦 工程目录：temp/code_output/vue_project_").append(appId).append('\n');
        sb.append("🔨 正在后台构建（首次需 npm install，约 1-3 分钟），构建完成后右侧预览自动可见\n\n");
        sb.append("```tree\n");
        sb.append("vue_project_").append(appId).append("/\n");
        sb.append(tree);
        sb.append("```\n");
        return sb.toString();
    }

    /* ---------------- 内部实现 ---------------- */

    private void renderChildren(Node dir, String prefix, StringBuilder sb, int nameColumn, int descColumn) {
        List<Node> children = new ArrayList<>(dir.children.values());
        // 目录在前、文件在后，同类按名称排序，保证结构稳定
        children.sort(Comparator.comparing((Node n) -> n.file != null).thenComparing(n -> n.name));
        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            boolean last = i == children.size() - 1;
            String lineStart = prefix + (last ? "└── " : "├── ");
            if (child.file == null) {
                // 目录
                sb.append(lineStart).append(child.name).append("/\n");
                renderChildren(child, prefix + (last ? "    " : "│   "), sb, nameColumn, descColumn);
            } else {
                // 文件：对齐 名称 / 说明 / 行数 大小
                String name = padRight(child.name, nameColumn - prefix.length() - 4);
                String desc = padRight(describe(child.file.path()), descColumn);
                String stat = child.file.success()
                        ? String.format("%d 行 · %s", child.file.lines(), humanSize(child.file.bytes()))
                        : "写入失败";
                sb.append(lineStart).append(name).append("  ").append(desc).append("  ·  ").append(stat).append('\n');
            }
        }
    }

    /**
     * 收集所有节点（用于计算列宽），level 从 1 开始
     */
    private List<Node> collect(Node dir, int level) {
        List<Node> all = new ArrayList<>();
        for (Node child : dir.children.values()) {
            child.level = level;
            all.add(child);
            all.addAll(collect(child, level + 1));
        }
        return all;
    }

    private int depth(Node node) {
        return Math.max(node.level - 1, 0);
    }

    /**
     * 文件说明：优先精确匹配常见文件，否则按路径/后缀推断
     */
    private String describe(String path) {
        String known = KNOWN_DESCRIPTIONS.get(path);
        if (known != null) {
            return known;
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".vue")) {
            if (lower.contains("/pages/") || lower.contains("/views/")) {
                return "页面组件";
            }
            if (lower.contains("/components/")) {
                return "公共组件";
            }
            return "Vue 组件";
        }
        if (lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less")) {
            return "样式文件";
        }
        if (lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".mjs")) {
            if (lower.contains("router")) {
                return "路由配置";
            }
            if (lower.contains("store") || lower.contains("pinia")) {
                return "状态管理";
            }
            if (lower.contains("data") || lower.contains("mock") || lower.contains("api")) {
                return "数据/接口";
            }
            if (lower.contains("util") || lower.contains("helper")) {
                return "工具函数";
            }
            return "脚本逻辑";
        }
        if (lower.endsWith(".json")) {
            return "配置/数据";
        }
        if (lower.endsWith(".html")) {
            return "页面模板";
        }
        if (lower.endsWith(".md")) {
            return "说明文档";
        }
        if (lower.endsWith(".svg") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp")) {
            return "静态资源";
        }
        return "工程文件";
    }

    /**
     * 统计工程目录下该文件的行数（文件不存在或读取失败返回 0）
     */
    private int countLines(String relativePath) {
        try {
            Path path = projectFile(relativePath);
            if (!Files.exists(path)) {
                return 0;
            }
            return Files.readAllLines(path).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private long fileSize(String relativePath) {
        try {
            File file = projectFile(relativePath).toFile();
            return file.exists() ? file.length() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Path projectFile(String relativePath) {
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId)
                .toPath()
                .resolve(relativePath);
    }

    /**
     * 从 FileWriteTool 的返回值里解析相对路径
     *
     * @param rawResult 工具返回值，形如「文件写入成功: src/App.vue」或「文件写入失败: src/App.vue, 错误: xxx」
     * @return 相对路径；解析不出来返回 null
     */
    public static String parsePathFromResult(String rawResult) {
        String result = StrUtil.nullToEmpty(rawResult).trim();
        if (result.startsWith(WRITE_OK_PREFIX)) {
            return result.substring(WRITE_OK_PREFIX.length()).trim();
        }
        if (result.startsWith(WRITE_FAIL_PREFIX)) {
            String rest = result.substring(WRITE_FAIL_PREFIX.length());
            int idx = rest.indexOf(ERROR_SPLIT);
            return (idx >= 0 ? rest.substring(0, idx) : rest).trim();
        }
        return null;
    }

    /**
     * 是否是「文件写入」工具的结果
     */
    public static boolean isFileWriteResult(String rawResult) {
        String result = StrUtil.nullToEmpty(rawResult).trim();
        return result.startsWith(WRITE_OK_PREFIX) || result.startsWith(WRITE_FAIL_PREFIX);
    }

    private static String humanSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /**
     * 按显示宽度右侧补空格（中文按 2 列计算，保证 <pre> 里能对齐）
     */
    private static String padRight(String text, int width) {
        int current = displayWidth(text);
        if (current >= width) {
            return text;
        }
        return text + " ".repeat(width - current);
    }

    private static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 常见 CJK / 全角字符按 2 列计算
            width += (c >= 0x1100 && (c <= 0x115F || c == 0x2329 || c == 0x232A
                    || (c >= 0x2E80 && c <= 0xA4CF)
                    || (c >= 0xAC00 && c <= 0xD7A3)
                    || (c >= 0xF900 && c <= 0xFAFF)
                    || (c >= 0xFE30 && c <= 0xFE6F)
                    || (c >= 0xFF00 && c <= 0xFF60)
                    || (c >= 0xFFE0 && c <= 0xFFE6))) ? 2 : 1;
        }
        return width;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /**
     * 一次文件写入的记录
     */
    private record FileItem(String path, boolean success, int lines, long bytes) {
    }

    /**
     * 文件结构树节点
     */
    private static final class Node {

        private final String name;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private FileItem file;
        private int level = 1;

        private Node(String name) {
            this.name = name;
        }
    }
}
