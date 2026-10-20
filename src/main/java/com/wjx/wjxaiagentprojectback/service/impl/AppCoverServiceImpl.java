package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.util.StrUtil;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import com.wjx.wjxaiagentprojectback.damon.entity.App;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.mapper.AppMapper;
import com.wjx.wjxaiagentprojectback.service.AppCoverService;
import com.wjx.wjxaiagentprojectback.service.ScreenshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * 应用封面 服务层实现。
 *
 * @author wjx
 */
@Service
@Slf4j
public class AppCoverServiceImpl implements AppCoverService {

    /**
     * 截图前预检目标页面的超时时间（毫秒）。
     * 静态资源刚写完、部署目录刚拷完时可能还没就绪，先探一次避免把 404/403 错误页截成封面。
     */
    private static final int REACHABLE_TIMEOUT_MS = 5000;

    /**
     * 这里用 AppMapper 而不是 AppService：AppServiceImpl 间接依赖流处理器，
     * 流处理器又要触发封面刷新，用 Service 会形成循环依赖。
     */
    @Resource
    private AppMapper appMapper;

    @Resource
    private ScreenshotService screenshotService;

    /**
     * 后端自身端口与上下文路径：未部署的应用截的是后端的静态预览地址
     */
    @Value("${server.port:8123}")
    private int serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Override
    public String buildCoverTargetUrl(App app) {
        ThrowUtils.throwIf(app == null || app.getId() == null, ErrorCode.PARAM_ERROR, "应用信息不能为空");
        // 1. 已部署：截部署地址，三种代码生成类型规则一致（Vue 工程的 dist 内容已拷到部署目录根下）
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, app.getDeployKey());
        }
        // 2. 未部署：截后端静态预览地址（与前端 STATIC_BASE_URL = http://localhost:8123/api/static 一致）
        String previewBase = String.format("http://localhost:%d%s/static", serverPort, StrUtil.nullToEmpty(contextPath));
        String dirName = app.getCodeGenType() + "_" + app.getId();
        CodeTypeEnum codeTypeEnum = CodeTypeEnum.getValueCode(app.getCodeGenType());
        if (codeTypeEnum == CodeTypeEnum.VUE_PROJECT) {
            // Vue 工程模式的构建产物在工程的 dist 目录下，浏览地址要追加 dist/index.html
            return String.format("%s/%s/dist/index.html", previewBase, dirName);
        }
        // HTML 单文件 / 多文件模式：产物根目录下就是 index.html
        return String.format("%s/%s/", previewBase, dirName);
    }

    @Override
    public String refreshCoverNow(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用 ID 不能为空");
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND, "应用不存在");
        // 1. 计算要截图的地址
        String targetUrl = buildCoverTargetUrl(app);
        // 2. 预检：目标页面必须真的能打开，否则会把 404/403 错误页截成封面
        ThrowUtils.throwIf(!isHtmlPageReachable(targetUrl), ErrorCode.OPERATION_ERROR,
                "封面截图失败：目标页面暂不可访问 " + targetUrl);
        // 3. 无头浏览器截图 + 上传对象存储
        String coverUrl = screenshotService.generateAndUploadScreenshot(targetUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(coverUrl), ErrorCode.OPERATION_ERROR, "封面截图上传失败");
        // 4. 只更新 cover 字段（update 默认忽略 null 字段，其它字段不会被清空）
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCover(coverUrl);
        appMapper.update(updateApp);
        log.info("应用封面已刷新: appId={}, codeGenType={}, deployKey={}, 截图地址={}, 封面={}",
                appId, app.getCodeGenType(), app.getDeployKey(), targetUrl, coverUrl);
        return coverUrl;
    }

    @Override
    public void refreshCoverAsync(Long appId) {
        // 虚拟线程异步执行，任务失败不能影响代码生成 / 部署的主流程
        Thread.startVirtualThread(() -> {
            try {
                refreshCoverNow(appId);
            } catch (Exception e) {
                // 失败时保留应用原有封面（不写 null），只记录日志
                log.error("异步刷新应用封面失败，已保留原封面: appId={}, 原因={}", appId, e.getMessage(), e);
            }
        });
    }

    /**
     * 预检目标页面是否为可访问的 HTML 页面
     *
     * @param url 目标地址
     * @return 可访问且返回 HTML 时为 true
     */
    private boolean isHtmlPageReachable(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(REACHABLE_TIMEOUT_MS);
            connection.setReadTimeout(REACHABLE_TIMEOUT_MS);
            int status = connection.getResponseCode();
            String contentType = StrUtil.nullToEmpty(connection.getContentType()).toLowerCase();
            boolean reachable = status == HttpURLConnection.HTTP_OK && contentType.contains("html");
            if (!reachable) {
                log.warn("封面目标页面不可用: url={}, status={}, contentType={}", url, status, contentType);
            }
            return reachable;
        } catch (Exception e) {
            log.warn("封面目标页面访问异常: url={}, 原因={}", url, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
