package com.wjx.wjxaiagentprojectback.service;

import com.wjx.wjxaiagentprojectback.damon.entity.App;

/**
 * 应用封面 服务层。
 * 负责把「应用页面截图」生成出来并写回 app.cover，三种代码生成类型共用同一套逻辑：
 * 1. 已部署（deployKey 非空）→ 截部署地址（AppConstant.CODE_DEPLOY_HOST，即 8090 静态服务）
 * 2. 未部署 → 截后端的静态预览地址（/api/static/{codeGenType}_{appId}/，Vue 工程追加 dist/index.html）
 * 截图目标地址的规则与前端 wjx-ai-agent-project-fronted/src/utils/app.ts 的
 * getVisitUrl / getPreviewUrl 完全一致，避免前后端两套地址对不上。
 *
 * @author wjx
 */
public interface AppCoverService {

    /**
     * 计算应用封面要截图的目标地址
     *
     * @param app 应用信息
     * @return 目标地址（部署地址优先，未部署则用静态预览地址）
     */
    String buildCoverTargetUrl(App app);

    /**
     * 同步生成封面并写库
     *
     * @param appId 应用 ID
     * @return 新的封面地址（对象存储 COS）
     */
    String refreshCoverNow(Long appId);

    /**
     * 异步生成封面并写库（虚拟线程），失败只记录日志、保留应用原有封面
     *
     * @param appId 应用 ID
     */
    void refreshCoverAsync(Long appId);
}
