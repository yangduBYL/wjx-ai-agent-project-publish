package com.wjx.wjxaiagentprojectback.concurrency;

import com.wjx.wjxaiagentprojectback.ai.AiCodeGenTypeRoutingService;
import com.wjx.wjxaiagentprojectback.ai.AiCodeGenTypeRoutingServiceFactory;
import com.wjx.wjxaiagentprojectback.ai.model.CodeTypeRoutingResult;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 代码生成类型路由服务的并发测试（Java 21 虚拟线程）。
 * <p>
 * 相比「起 N 个线程 + join」的写法，这里补上了 4 件必须做的事：
 * <ol>
 *   <li><b>收集异常</b>：线程里抛出的异常不会自己冒出来，必须存起来最后断言，否则测试会「假绿」；</li>
 *   <li><b>真的断言结果</b>：不能只打印，要校验「路由结果 == 预期类型」，同一个 prompt 并发跑多次结果还要一致；</li>
 *   <li><b>验证多例本身</b>：并发拿到的必须是不同的服务实例（这正是 @Scope("prototype") 的意义）；</li>
 *   <li><b>用 newVirtualThreadPerTaskExecutor + try-with-resources</b>：自动等待、自动关闭，不会漏任务。</li>
 * </ol>
 * 会真实调用大模型（每次路由只有几十个 token，很便宜）；如果想默认不跑，给类加上
 * {@code @EnabledIfSystemProperty(named = "ai.routing.e2e", matches = "true")}。
 */
@SpringBootTest
@DisplayName("代码生成类型路由：虚拟线程并发")
class AiCodeGenTypeRoutingConcurrencyTest {

    /**
     * 用例：提示词 -> 期望类型（三条都严格对应 prompt/codegen-routing-system-prompt.txt 的判断规则）
     */
    private static final Map<String, CodeTypeEnum> CASES = new LinkedHashMap<>();

    static {
        CASES.put("做一个只有一页的个人简介 HTML 页面，不用拆分文件", CodeTypeEnum.HTML);
        CASES.put("做一个多页面的静态网站，把 HTML、CSS、JS 拆成多个文件", CodeTypeEnum.MULTI_FILE);
        CASES.put("做一个带登录、路由和数据管理的 Vue 后台管理系统", CodeTypeEnum.VUE_PROJECT);
    }

    /** 每个 prompt 并发调用几次（多次并发才能同时验证「结果正确」和「并发不串」） */
    private static final int ROUNDS_PER_CASE = 3;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory routingServiceFactory;

    /**
     * 一次路由调用的结果
     *
     * @param rawCodeGenType 模型原始返回的字段值（便于排查）
     */
    private record TaskResult(String prompt, CodeTypeEnum expected, int round,
                              String rawCodeGenType, CodeTypeEnum actual, long costMs, String error) {
    }

    @Test
    @DisplayName("3 类需求 × 3 个虚拟线程并发：结果正确、同一 prompt 的并发结果一致、无异常")
    void concurrentRoutingIsCorrectAndStable() throws Exception {
        List<TaskResult> results = Collections.synchronizedList(new ArrayList<>());
        long wallStart = System.nanoTime();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Map.Entry<String, CodeTypeEnum> entry : CASES.entrySet()) {
                for (int round = 1; round <= ROUNDS_PER_CASE; round++) {
                    final String prompt = entry.getKey();
                    final CodeTypeEnum expected = entry.getValue();
                    final int currentRound = round;
                    futures.add(executor.submit(() -> {
                        long start = System.nanoTime();
                        String rawCodeGenType = null;
                        CodeTypeEnum actual = null;
                        String error = null;
                        try {
                            // 每个虚拟线程各拿一个新的服务实例（prototype）
                            AiCodeGenTypeRoutingService service =
                                    routingServiceFactory.createAiCodeGenTypeRoutingService();
                            CodeTypeRoutingResult result = service.routeCodeGenType(prompt);
                            if (result != null) {
                                rawCodeGenType = result.codeGenType();
                                actual = result.toCodeType();
                            }
                        } catch (Exception e) {
                            // 关键：线程内的异常必须收集起来，否则测试会假绿
                            error = e.getClass().getSimpleName() + ": " + e.getMessage();
                        }
                        long costMs = (System.nanoTime() - start) / 1_000_000;
                        results.add(new TaskResult(prompt, expected, currentRound,
                                rawCodeGenType, actual, costMs, error));
                    }));
                }
            }
            // 等所有任务结束（try-with-resources 关闭时也会等，这里显式等待便于超时保护）
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.MINUTES);
            }
        }

        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;

        // ---- 组装报告 ----
        StringBuilder report = new StringBuilder();
        report.append("### 路由服务虚拟线程并发测试\n\n");
        report.append("| 需求提示词 | 期望类型 | 第几次 | 模型原始返回 | 解析后类型 | 耗时(ms) | 异常 |\n");
        report.append("|---|---|---|---|---|---|---|\n");
        long sumMs = 0;
        for (TaskResult r : results) {
            sumMs += r.costMs();
            report.append(String.format("| %s | %s | %d | %s | %s | %d | %s |%n",
                    r.prompt(), r.expected(), r.round(),
                    r.rawCodeGenType() == null ? "-" : r.rawCodeGenType(),
                    r.actual() == null ? "-" : r.actual().name(), r.costMs(),
                    r.error() == null ? "-" : r.error()));
        }
        report.append("\n- 并发任务数 = ").append(results.size())
                .append("（").append(CASES.size()).append(" 类需求 × ").append(ROUNDS_PER_CASE).append(" 次）\n");
        report.append("- 整体墙钟耗时 = ").append(wallMs).append(" ms，各任务耗时之和 = ").append(sumMs).append(" ms")
                .append(sumMs > wallMs ? "（墙钟 < 之和 => 确实在并发跑）" : "").append('\n');

        Map<CodeTypeEnum, Integer> distribution = new LinkedHashMap<>();
        for (TaskResult r : results) {
            if (r.actual() != null) {
                distribution.merge(r.actual(), 1, Integer::sum);
            }
        }
        report.append("- 路由结果分布 = ").append(distribution).append('\n');

        writeReport("路由并发测试.md", report.toString());
        System.out.println(report);

        // ---- 断言 ----
        Assertions.assertEquals(CASES.size() * ROUNDS_PER_CASE, results.size(), "有任务没跑完");
        for (TaskResult r : results) {
            Assertions.assertNull(r.error(), "prompt「" + r.prompt() + "」抛异常: " + r.error());
            Assertions.assertNotNull(r.actual(), "prompt「" + r.prompt() + "」返回了无法识别的类型: "
                    + r.rawCodeGenType() + "（提示词里的可选类型要与之严格对齐）");
            Assertions.assertEquals(r.expected(), r.actual(),
                    "prompt「" + r.prompt() + "」路由错误：期望 " + r.expected() + "，实际 " + r.actual()
                            + "（若只是偶发跑偏，多半是路由提示词写得不够细，或把模型 temperature 调低到 0）");
        }
    }

    @Test
    @DisplayName("prototype 生效：并发拿到的路由服务实例互不相同（同一实例不会跨线程共享）")
    void routingServiceFactoryCreatesNewInstancePerCall() throws Exception {
        int tasks = 20;
        Set<AiCodeGenTypeRoutingService> instances = Collections.newSetFromMap(new IdentityHashMap<>());
        List<AiCodeGenTypeRoutingService> collected = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() ->
                        collected.add(routingServiceFactory.createAiCodeGenTypeRoutingService())));
            }
            for (Future<?> future : futures) {
                future.get(1, TimeUnit.MINUTES);
            }
        }
        instances.addAll(collected);

        System.out.println("[路由服务] 并发创建 " + tasks + " 次，不同实例数 = " + instances.size());
        Assertions.assertEquals(tasks, instances.size(),
                "prototype 没生效：有线程拿到了同一个路由服务实例");

        // 同一线程里连续取两次也应该是不同实例（prototype 的定义）
        AiCodeGenTypeRoutingService first = routingServiceFactory.createAiCodeGenTypeRoutingService();
        AiCodeGenTypeRoutingService second = routingServiceFactory.createAiCodeGenTypeRoutingService();
        Assertions.assertNotSame(first, second, "createAiCodeGenTypeRoutingService() 每次都应返回新实例");
    }

    private void writeReport(String fileName, String content) {
        try {
            Path dir = Path.of("temp", "test_report");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("写测试报告失败: " + e.getMessage());
        }
    }
}
