package com.wjx.wjxaiagentprojectback.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;

/**
 * web 网页截图工具类（Spring Bean）
 *
 * 设计要点（2026-10 修复 `NoSuchSessionException: invalid session id` 那个 bug）：
 * 1. 浏览器实例懒加载 + 加锁串行使用：WebDriver 不是线程安全的，封面刷新跑在虚拟线程上，
 *    多个线程同时 get()/截图会互相打断，甚至让 ChromeDriver 直接把这个 session 判废。
 * 2. 会话自愈：每次使用前探活（getWindowHandles），发现 session 已失效
 *    （无头 Chrome 崩了、被 taskkill 杀了、chromedriver 被回收等）就丢掉旧实例重新拉起一个；
 *    截图过程中抛 WebDriverException 也会重建，并整体重试一次。
 *    原来的实现是 static 单例且一旦失效就永久失效 —— 这就是"启动后一开始正常，
 *    后来所有截图全部报 invalid session id"的原因。
 * 3. 必须是 Spring Bean 才吃得到 @PreDestroy：原来的类不是 Bean，destroy() 从来没执行过，
 *    chrome.exe 会一直残留到进程被杀。
 */
@Component
@Slf4j
public class WebScreenshotUtils {

    /** 默认截图窗口尺寸 */
    private static final int DEFAULT_WIDTH = 1600;

    private static final int DEFAULT_HEIGHT = 900;

    /** 单个 URL 的最大尝试次数（第 1 次失败就重建会话重试 1 次） */
    private static final int MAX_ATTEMPTS = 2;

    /** 浏览器实例与访问锁：WebDriver 非线程安全，同一时刻只允许一个线程驱动浏览器 */
    private final Object driverLock = new Object();

    /** 无头浏览器实例，懒加载；失效时会被置空并重建 */
    private volatile WebDriver webDriver;

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        // 加锁：封面刷新是虚拟线程并发的，必须串行使用同一个浏览器实例
        synchronized (driverLock) {
            WebDriverException lastError = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    return doSaveWebPageScreenshot(getAvailableDriver(), webUrl);
                } catch (WebDriverException e) {
                    // 浏览器侧出问题（页面崩溃/会话被删/浏览器进程没了）：先丢掉这个实例，
                    // 避免坏会话污染后续调用
                    discardDriver();
                    lastError = e;
                    if (e instanceof TimeoutException) {
                        // 页面一直加载不完 or Selenium 客户端读超时（默认 3 分钟）：
                        // 重试只会让等待翻倍，这里直接失败，让下一次调用拿新会话重试
                        log.error("网页截图失败（超时，不重试）: url={}, 原因={}", webUrl,
                                e.getClass().getSimpleName() + ": " + StrUtil.maxLength(e.getMessage(), 200), e);
                        return null;
                    }
                    // 本机实测：新起的无头 Chrome 第一次导航有一定概率把页面/会话搞崩，
                    // 重建浏览器重试一次基本都能成功（这正是"invalid session id"那条报错的兜底）
                    log.warn("网页截图失败（第 {}/{} 次），重建浏览器会话后重试: url={}, 原因={}",
                            attempt, MAX_ATTEMPTS, webUrl,
                            e.getClass().getSimpleName() + ": " + StrUtil.maxLength(e.getMessage(), 200));
                } catch (Exception e) {
                    // 图片压缩/落盘等非浏览器错误：会话是好的，不用重建
                    log.error("网页截图失败: {}", webUrl, e);
                    return null;
                }
            }
            log.error("网页截图失败，已重试 {} 次仍失败: {}", MAX_ATTEMPTS, webUrl, lastError);
            return null;
        }
    }

    /**
     * 真正的截图流程（需要外部已持有 driverLock）
     *
     * @param driver 可用（活的）浏览器实例
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径
     */
    private String doSaveWebPageScreenshot(WebDriver driver, String webUrl) {
        // 创建临时目录
        String rootPath = System.getProperty("user.dir") + File.separator + "temp" + File.separator + "screenshots"
                + File.separator + UUID.randomUUID().toString().substring(0, 8);
        FileUtil.mkdir(rootPath);
        // 图片后缀
        final String IMAGE_SUFFIX = ".png";
        // 原始截图文件路径
        String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
        // 访问网页
        driver.get(webUrl);
        // 等待页面加载完成
        waitForPageLoad(driver);
        // 截图
        byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        // 保存原始图片
        saveImage(screenshotBytes, imageSavePath);
        log.info("原始截图保存成功: {}", imageSavePath);
        // 压缩图片
        final String COMPRESSION_SUFFIX = "_compressed.jpg";
        String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
        compressImage(imageSavePath, compressedImagePath);
        log.info("压缩图片保存成功: {}", compressedImagePath);
        // 删除原始图片，只保留压缩图片
        FileUtil.del(imageSavePath);
        return compressedImagePath;
    }

    /**
     * 取一个可用的浏览器实例：没有就创建，失效了就重建
     */
    private WebDriver getAvailableDriver() {
        if (webDriver != null && !isSessionAlive(webDriver)) {
            log.warn("检测到无头浏览器会话已失效（浏览器进程已不存在），准备重建");
            discardDriver();
        }
        if (webDriver == null) {
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            log.info("无头 Chrome 实例已启动");
        }
        return webDriver;
    }

    /**
     * 探活：session 失效（浏览器崩溃/被外部杀掉）时 ChromeDriver 会抛
     * NoSuchSessionException，这里统一转成 false
     */
    private boolean isSessionAlive(WebDriver driver) {
        try {
            driver.getWindowHandles();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 丢弃当前浏览器实例（quit 本身失败也不能影响主流程）
     */
    private void discardDriver() {
        WebDriver oldDriver = webDriver;
        webDriver = null;
        if (oldDriver != null) {
            try {
                oldDriver.quit();
            } catch (Exception e) {
                // 浏览器已经死了，quit 报错正常，忽略
                log.debug("关闭失效的浏览器实例时异常（忽略）: {}", e.getMessage());
            }
        }
    }

    /**
     * 应用关闭时回收浏览器进程（本类是 Spring Bean，这个注解才会真正生效）
     */
    @PreDestroy
    public void destroy() {
        synchronized (driverLock) {
            if (webDriver != null) {
                discardDriver();
                log.info("无头 Chrome 实例已关闭");
            }
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 自动管理 ChromeDriver
            WebDriverManager.chromedriver().setup();
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            // 无头模式
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }
}
