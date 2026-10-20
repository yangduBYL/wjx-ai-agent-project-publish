package com.wjx.wjxaiagentprojectback.concurrency;

import com.wjx.wjxaiagentprojectback.ai.AiChatService;
import com.wjx.wjxaiagentprojectback.ai.AiChatServiceFactory;
import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFacade;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import dev.langchain4j.model.chat.StreamingChatModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java 21 虚拟线程并发测试。
 * <p>
 * 目的：把「ChatModel 从单例改成 Spring 多例（@Scope("prototype")）」这件事**变成看得见的数字**。
 * <ul>
 *   <li>场景 1：多例 bean 每次 getBean 都是新实例（并发生成 N 个独立模型对象，单例则永远是同一个）；</li>
 *   <li>场景 2：并发构建 AiChatService —— 不同 appId 各自持有独立模型，同一 appId 命中缓存复用；</li>
 *   <li>场景 3：并发 vs 串行创建服务的耗时对比（虚拟线程不排队）；</li>
 *   <li>场景 4（默认关闭，需要真调 DeepSeek、花额度）：N 个虚拟线程同时生成 N 个 Vue 工程，
 *       验证互不串台、各自落盘到 vue_project_{appId}。</li>
 * </ul>
 * 报告同时写一份 UTF-8 的 markdown 到 temp/test_report/，方便查看（控制台在 git-bash 里是 GBK 会乱码）。
 */
@SpringBootTest
@DisplayName("Java 21 虚拟线程并发测试")
class VirtualThreadConcurrencyTest {

    private static final String REASONING_PROTOTYPE_BEAN = "reasoningStreamingChatModelPrototype";
    private static final String NORMAL_PROTOTYPE_BEAN = "streamingChatModelPrototype";

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private AiChatServiceFactory aiChatServiceFactory;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    // ------------------------------------------------------------------ 场景 1

    @Test
    @DisplayName("场景1：prototype 模型 32 个虚拟线程并发生成 32 个不同实例；单例 bean 永远是同一个")
    void prototypeBeansAreDistinctPerLookup() throws Exception {
        int threads = 32;
        List<String> rows = Collections.synchronizedList(new ArrayList<>());

        // 用 IdentityHashMap 判断「是不是同一个对象」（不能用 equals）
        Set<StreamingChatModel> distinctInstances =
                Collections.newSetFromMap(new IdentityHashMap<>());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<StreamingChatModel>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    long start = System.nanoTime();
                    StreamingChatModel model =
                            applicationContext.getBean(REASONING_PROTOTYPE_BEAN, StreamingChatModel.class);
                    long costMs = (System.nanoTime() - start) / 1_000_000;
                    rows.add(String.format("| 虚拟线程 #%02d | %-22s | identityHash=%d | 创建耗时=%dms |",
                            index, Thread.currentThread(), System.identityHashCode(model), costMs));
                    return model;
                }));
            }
            for (Future<StreamingChatModel> future : futures) {
                distinctInstances.add(future.get(60, TimeUnit.SECONDS));
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("### 场景1：prototype 每次 getBean 都是新实例\n\n");
        report.append("| 虚拟线程 | 线程对象 | 模型实例 | 创建耗时 |\n|---|---|---|---|\n");
        rows.forEach(row -> report.append(row).append('\n'));
        report.append("\n- 并发请求 ").append(threads).append(" 次 `getBean(\"").append(REASONING_PROTOTYPE_BEAN).append("\")`\n");
        report.append("- 得到的不同模型实例数 = **").append(distinctInstances.size()).append("**\n");
        report.append("- 结论：").append(distinctInstances.size() == threads
                ? "多例生效，每个请求持有自己的流式模型 ✅\n" : "多例没生效，有实例被共享 ❌\n");

        // 对照：单例 bean 反复 getBean 必然是同一个对象
        if (applicationContext.containsBean("openAiStreamingChatModel")) {
            Object first = applicationContext.getBean("openAiStreamingChatModel");
            Object second = applicationContext.getBean("openAiStreamingChatModel");
            report.append("- 对照（单例 `openAiStreamingChatModel`）：两次 getBean 是否同一个对象 = ")
                    .append(first == second ? "是（单例共享）" : "否").append('\n');
            assertSame(first, second, "单例 bean 必须是同一个实例");
        }

        writeReport("场景1-prototype多例.md", report.toString());
        System.out.println(report);
        assertEquals(threads, distinctInstances.size(), "prototype bean 必须每次都是新实例");
    }

    // ------------------------------------------------------------------ 场景 2

    @Test
    @DisplayName("场景2：8 个虚拟线程并发为 8 个 appId 建服务，实例互不相同；同一 appId 命中缓存")
    void concurrentServiceCreationIsIsolatedAndCached() throws Exception {
        int threads = 8;
        long baseAppId = 9_100_000_000_000_000L;
        List<AiChatService> services = Collections.synchronizedList(new ArrayList<>());
        StringBuilder report = new StringBuilder("### 场景2：并发构建 AiChatService（不同 appId 互不共享）\n\n");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AiChatService>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final long appId = baseAppId + i;
                futures.add(executor.submit(() -> {
                    long start = System.nanoTime();
                    AiChatService service = aiChatServiceFactory.getAiChatService(appId, CodeTypeEnum.VUE_PROJECT);
                    long costMs = (System.nanoTime() - start) / 1_000_000;
                    report.append(String.format("- appId=%d -> service identityHash=%d（耗时 %dms）%n",
                            appId, System.identityHashCode(service), costMs));
                    return service;
                }));
            }
            for (Future<AiChatService> future : futures) {
                services.add(future.get(120, TimeUnit.SECONDS));
            }
        }

        Set<AiChatService> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(services);
        report.append("\n- 并发次数 = ").append(threads).append("，不同服务实例数 = **").append(distinct.size()).append("**\n");
        assertEquals(threads, distinct.size(), "不同 appId 必须拿到不同的 AiChatService 实例");

        // 同一 appId 重复取：命中 Caffeine 缓存（同一个实例 -> 对话记忆得以延续）
        AiChatService first = aiChatServiceFactory.getAiChatService(baseAppId, CodeTypeEnum.VUE_PROJECT);
        AiChatService second = aiChatServiceFactory.getAiChatService(baseAppId, CodeTypeEnum.VUE_PROJECT);
        assertSame(first, second, "同一 appId 必须命中缓存，否则对话记忆每次都会重建");
        report.append("\n- 同一 appId 连续两次取服务：同一实例 = ")
                .append(first == second ? "是（缓存命中）" : "否")
                .append("\n- 注意：同一个 appId 的并发请求共用一份 ChatMemory（同一个 AiChatService 实例），\n")
                .append("  这就是「多例解决的是不同应用之间的并发」；同一应用被并发写入时\n")
                .append("  需要额外的串行化或按请求建实例，属于下一步优化项。\n");

        writeReport("场景2-并发构建服务.md", report.toString());
        System.out.println(report);
    }

    // ------------------------------------------------------------------ 场景 3

    @Test
    @DisplayName("场景3：20 个 appId 建服务，串行耗时 vs 虚拟线程并发耗时")
    void virtualThreadsBeatSerialCreation() throws Exception {
        int count = 20;
        // 每一轮都用全新的 appId，避免命中 Caffeine 缓存导致「并发比串行快」变成假的
        long baseAppId = 9_200_000_000_000_000L;

        // 预热：先让 JIT、MySQL 连接池、Redis 连接热起来，减少首轮偏置
        List<Long> warmup = appIds(baseAppId, 5);
        for (Long appId : warmup) {
            aiChatServiceFactory.getAiChatService(appId, CodeTypeEnum.VUE_PROJECT);
        }

        long serial1 = measureSerial(appIds(baseAppId + 1_000, count));
        long parallel1 = measureParallel(appIds(baseAppId + 2_000, count));
        long serial2 = measureSerial(appIds(baseAppId + 3_000, count));
        long parallel2 = measureParallel(appIds(baseAppId + 4_000, count));

        long serialMs = Math.min(serial1, serial2);
        long parallelMs = Math.min(parallel1, parallel2);

        String report = String.format("""
                ### 场景3：串行 vs 虚拟线程并发（%d 个 appId 各建一次 AI 服务，每轮都用全新 appId）

                | 方式 | 第1轮 | 第2轮 | 取较快值 |
                |---|---|---|---|
                | 串行 for 循环 | %d ms | %d ms | **%d ms** |
                | 虚拟线程并发 | %d ms | %d ms | **%d ms** |

                - 加速比 ≈ %.2fx，平均每个 appId：串行 %.1f ms → 并发 %.1f ms
                - 每个任务都要查一次 MySQL + Redis 加载历史对话，属于真实 IO；虚拟线程让这些 IO 等待重叠
                - 注意：这里刻意避开了缓存，否则第二次取同一个 appId 会命中 Caffeine（≈0ms），对比就没意义了
                """, count, serial1, serial2, serialMs, parallel1, parallel2, parallelMs,
                parallelMs == 0 ? 0d : (double) serialMs / parallelMs,
                (double) serialMs / count, (double) parallelMs / count);

        writeReport("场景3-串行vs并发.md", report);
        System.out.println(report);
        assertTrue(parallelMs <= serialMs,
                "虚拟线程并发建服务不应该比串行还慢（串行=" + serialMs + "ms, 并发=" + parallelMs + "ms）");
    }

    private List<Long> appIds(long base, int count) {
        List<Long> appIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            appIds.add(base + i);
        }
        return appIds;
    }

    private long measureSerial(List<Long> appIds) {
        long start = System.nanoTime();
        for (Long appId : appIds) {
            aiChatServiceFactory.getAiChatService(appId, CodeTypeEnum.VUE_PROJECT);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private long measureParallel(List<Long> appIds) throws Exception {
        long start = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Long appId : appIds) {
                futures.add(executor.submit(() -> aiChatServiceFactory.getAiChatService(appId, CodeTypeEnum.VUE_PROJECT)));
            }
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // ------------------------------------------------------------------ 场景 4

    @Test
    @DisplayName("场景4（需 -Dvue.e2e=true）：N 个虚拟线程同时真调 DeepSeek 生成 N 个 Vue 工程")
    @EnabledIfSystemProperty(named = "vue.e2e", matches = "true")
    void concurrentVueProjectGenerationWritesIsolatedProjects() throws Exception {
        int tasks = Integer.getInteger("vue.e2e.tasks", 2);
        long baseAppId = Long.getLong("vue.e2e.baseAppId", 9_300_000_000_000_000L);
        String prompt = System.getProperty("vue.e2e.prompt",
                "生成一个极简的待办清单单页应用：只做 1 个页面（列表+新增+删除），"
                        + "文件总数不超过 4 个，总代码量不超过 80 行，不要多余页面和多余依赖。");

        record TaskResult(long appId, long startOffset, long endOffset, long fileCount,
                          boolean hasPackageJson, long toolCallCount, String error) {
        }

        AtomicLong wallStart = new AtomicLong(System.nanoTime());
        List<Future<TaskResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                final long appId = baseAppId + i;
                futures.add(executor.submit(() -> {
                    long start = System.nanoTime();
                    long startOffset = (start - wallStart.get()) / 1_000_000;
                    String error = null;
                    long toolCallCount = 0;
                    try {
                        List<String> chunks = aiCodeGeneratorFacade
                                .generateAndSaveCodeStream(prompt, CodeTypeEnum.VUE_PROJECT, appId)
                                .collectList()
                                .block(Duration.ofMinutes(15));
                        String joined = chunks == null ? "" : String.join("", chunks);
                        // 统计模型真正发起的 writeFile 工具调用次数
                        toolCallCount = joined.split("writeFile", -1).length - 1;
                    } catch (Exception e) {
                        error = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                    long end = System.nanoTime();

                    Path projectDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
                    long fileCount = 0;
                    boolean hasPackageJson = Files.isRegularFile(projectDir.resolve("package.json"));
                    if (Files.isDirectory(projectDir)) {
                        try (var walk = Files.walk(projectDir)) {
                            fileCount = walk.filter(Files::isRegularFile).count();
                        }
                    }
                    return new TaskResult(appId, startOffset, (end - wallStart.get()) / 1_000_000,
                            fileCount, hasPackageJson, toolCallCount, error);
                }));
            }

            List<TaskResult> results = new ArrayList<>();
            for (Future<TaskResult> future : futures) {
                results.add(future.get(20, TimeUnit.MINUTES));
            }

            StringBuilder report = new StringBuilder();
            report.append("### 场景4：虚拟线程并发生成 Vue 工程（真实调用 DeepSeek）\n\n");
            report.append("提示词：").append(prompt).append("\n\n");
            report.append("| appId | 开始(ms) | 结束(ms) | 单次耗时(ms) | writeFile 调用次数 | 落盘文件数 | package.json | 异常 |\n");
            report.append("|---|---|---|---|---|---|---|---|\n");
            long maxOverlap = 0;
            for (TaskResult r : results) {
                report.append(String.format("| %d | %d | %d | %d | %d | %d | %s | %s |%n",
                        r.appId(), r.startOffset(), r.endOffset(), r.endOffset() - r.startOffset(),
                        r.toolCallCount(), r.fileCount(), r.hasPackageJson() ? "有" : "无",
                        r.error() == null ? "-" : r.error()));
            }
            for (int i = 0; i < results.size(); i++) {
                long overlap = 0;
                for (TaskResult other : results) {
                    if (other.startOffset() < results.get(i).endOffset()
                            && results.get(i).startOffset() < other.endOffset()) {
                        overlap++;
                    }
                }
                maxOverlap = Math.max(maxOverlap, overlap);
            }
            long wallMs = (System.nanoTime() - wallStart.get()) / 1_000_000;
            long sumMs = results.stream().mapToLong(r -> r.endOffset() - r.startOffset()).sum();
            report.append("\n- 整体墙钟耗时 = ").append(wallMs).append(" ms，各任务耗时之和 = ").append(sumMs).append(" ms\n");
            report.append("- 时间轴最大重叠任务数 = ").append(maxOverlap).append("（>1 说明确实是同时在跑）\n");

            writeReport("场景4-并发真实生成Vue工程.md", report.toString());
            System.out.println(report);

            for (TaskResult r : results) {
                assertNull(r.error(), "appId=" + r.appId() + " 生成过程抛异常: " + r.error());
                assertTrue(r.fileCount() >= 2, "appId=" + r.appId() + " 只落了 " + r.fileCount() + " 个文件，工具可能没生效");
                assertTrue(r.hasPackageJson(), "appId=" + r.appId() + " 缺少 package.json");
                assertTrue(r.toolCallCount() > 0, "appId=" + r.appId() + " 模型没有调用 writeFile 工具");
            }
            if (tasks > 1) {
                assertTrue(maxOverlap > 1, "没有任何两个任务在时间上重叠，并发没有真正发生");
            }
        }
    }

    // ------------------------------------------------------------------ 工具方法

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
