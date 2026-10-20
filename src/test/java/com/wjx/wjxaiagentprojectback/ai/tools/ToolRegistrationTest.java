package com.wjx.wjxaiagentprojectback.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 工具注册回归测试。
 * <p>
 * 背景（2026-10-14 的线上 bug）：重构时把工具挪到了 {@code ai.tools.toolsName} 包，
 * 但新的工具类只保留了「展示用」的方法（getToolName / generateToolExecutedResult），
 * 漏掉了带 {@code @Tool} 注解的真实方法。于是 {@code toolManager.getAllTools()}
 * 交给 langchain4j 后解析出来 0 个工具 —— 模型拿不到 writeFile，
 * 只能输出文字、一个文件都不写，随后 VueProjectBuilder 报「项目目录不存在」。
 * <p>
 * 这个测试就是那道防线：只要模型可见的工具数 != 注册的 bean 数，就立刻失败。
 */
@SpringBootTest
@DisplayName("工具注册回归：模型必须真的拿到可调用的工具")
class ToolRegistrationTest {

    /** 期望注册的工具（@Tool 方法名 == BaseTool.getToolName()） */
    private static final Set<String> EXPECTED_TOOLS =
            Set.of("writeFile", "readFile", "modifyFile", "deleteFile", "readDir");

    @Resource
    private ToolManager toolManager;

    @Test
    @DisplayName("每个 BaseTool bean 都要贡献一个 @Tool 方法，否则模型收到 0 个工具")
    void everyToolBeanIsVisibleToTheModel() {
        BaseTool[] tools = toolManager.getAllTools();
        assertNotNull(tools, "ToolManager 没有注入到任何工具 bean");

        // 逐个对象解析：与 AiServices.builder().tools(toolManager.getAllTools()) 的 varargs 展开方式完全一致
        // （注意：不能把整个数组当成一个对象丢给 toolSpecificationsFrom(Object)，那样按数组 Class 解析必然是 0 个）
        List<ToolSpecification> specifications = new ArrayList<>();
        for (Object tool : tools) {
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(tool));
        }

        Set<String> visibleToolNames = specifications.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        System.out.println("[工具注册] Spring bean 数 = " + tools.length
                + "，模型可见工具 = " + visibleToolNames);

        assertEquals(tools.length, visibleToolNames.size(),
                "有 BaseTool bean 没有暴露 @Tool 方法，模型拿不到这些工具（Vue 工程会落不了盘）");
        assertEquals(EXPECTED_TOOLS, visibleToolNames, "模型可见的工具集合与预期不一致");
    }

    @Test
    @DisplayName("getToolName() 必须与 @Tool 方法名一致，否则前端工具提示会拿不到实例")
    void toolNameMatchesToolMethodName() {
        for (BaseTool tool : toolManager.getAllTools()) {
            assertNotNull(toolManager.getTool(tool.getToolName()),
                    "ToolManager 用 getToolName() 查不到工具: " + tool.getToolName());
            assertNotNull(tool.getDisplayName(), "工具缺少中文展示名: " + tool.getToolName());
        }
    }

    @Test
    @DisplayName("工具 JSON Schema 的入参名必须是真实形参名（依赖编译期 -parameters，否则退化成 arg0）")
    void toolParameterNamesAreRealParameterNames() {
        Map<String, Set<String>> expectedParameters = Map.of(
                "writeFile", Set.of("relativeFilePath", "content"),
                "readFile", Set.of("relativeFilePath"),
                "modifyFile", Set.of("relativeFilePath", "oldContent", "newContent"),
                "deleteFile", Set.of("relativeFilePath"),
                "readDir", Set.of("relativeDirPath"));

        for (Object tool : toolManager.getAllTools()) {
            for (ToolSpecification specification : ToolSpecifications.toolSpecificationsFrom(tool)) {
                Set<String> expected = expectedParameters.get(specification.name());
                assertNotNull(expected, "测试未覆盖的工具: " + specification.name());

                Set<String> actual = ((JsonObjectSchema) specification.parameters()).properties().keySet();
                System.out.println("[工具参数] " + specification.name() + " -> " + actual);

                assertFalse(actual.stream().anyMatch(name -> name.startsWith("arg")),
                        specification.name() + " 的参数名退化成了 " + actual
                                + "：pom.xml 的 maven-compiler-plugin 需要开启 <parameters>true</parameters>；"
                                + "参数名退化时，模型只能靠描述猜参数，流式回显也会显示 null");
                assertEquals(expected, actual, specification.name() + " 的入参名与 @Tool 方法形参名不一致");
            }
        }
    }
}
