package com.wjx.wjxaiagentprojectback.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具基类
 * 定义所有工具的通用接口
 * <p>
 * 重要：子类必须暴露带 @Tool 注解的方法（方法名 == getToolName()），
 * langchain4j 只会把「带 @Tool 的方法」注册成模型可调用的工具；
 * 只有 getToolName()/generateXxx() 这类展示方法而缺少 @Tool 方法的类，
 * 会让模型收到 0 个工具（AI 无法写文件，Vue 工程目录就不会被创建）。
 */
public abstract class BaseTool {

    /** Vue 工程目录前缀（与 FileWriteTool 落盘规则保持一致） */
    public static final String PROJECT_DIR_PREFIX = "vue_project_";

    /** 读取类工具返回给模型的最大字符数，避免上下文被大文件撑爆 */
    protected static final int MAX_READ_CHARS = 15000;

    /** 目录树最多返回的条目数 */
    protected static final int MAX_DIR_ENTRIES = 200;

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    /**
     * 安全读取工具参数：取不到时返回占位文本，保证用户可见文案里永远不出现 null
     *
     * @param arguments 工具执行参数（可能为 null / 空对象）
     * @param key       参数名（与 @Tool 方法的形参名一致，依赖编译期 -parameters）
     * @return 参数值或占位文本
     */
    protected static String readArg(JSONObject arguments, String key) {
        if (arguments == null) {
            return "未提供";
        }
        return StrUtil.blankToDefault(arguments.getStr(key), "未提供");
    }

    /**
     * 当前 appId 对应的 Vue 工程根目录
     */
    protected Path projectRoot(Long appId) {
        return Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, PROJECT_DIR_PREFIX + appId);
    }

    /**
     * 把工具入参里的「相对路径」解析成工程内的绝对路径。
     * <p>
     * 1. 兼容模型把工程目录名一起带上来的写法（vue_project_123/src/main.js 等价于 src/main.js）；
     * 2. 归一化后必须仍然落在工程目录内，防止 ../ 越权到磁盘其它位置。
     *
     * @param filePath 文件/目录的相对路径
     * @param appId    应用 ID（由 langchain4j 的 @ToolMemoryId 注入）
     * @return 工程内的绝对路径
     */
    protected Path resolveProjectPath(String filePath, Long appId) {
        if (appId == null) {
            throw new IllegalArgumentException("appId 为空，无法定位工程目录");
        }
        if (StrUtil.isBlank(filePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        String normalized = filePath.replace('\\', '/').trim();
        // 去掉模型可能带上的工程目录前缀
        String prefix = PROJECT_DIR_PREFIX + appId + "/";
        if (normalized.startsWith(prefix)) {
            normalized = normalized.substring(prefix.length());
        }
        // 去掉开头的 "/" 或 "./"
        while (normalized.startsWith("/") || normalized.startsWith("./")) {
            normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized.substring(2);
        }
        Path root = projectRoot(appId).normalize();
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法路径（不允许访问工程目录之外）: " + filePath);
        }
        return resolved;
    }
}
