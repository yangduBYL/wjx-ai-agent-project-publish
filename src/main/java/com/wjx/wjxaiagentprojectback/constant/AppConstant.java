package com.wjx.wjxaiagentprojectback.constant;

/**
 * 应用常量，优先级
 */
public interface AppConstant {
   Integer GOOD_APP_PRIORITY = 99;
   Integer DEFAULT_APP_PRIORITY = 0;
    /**
     * 应用生成目录
     *
     */
    String CODE_OUTPUT_ROOT_DIR=System.getProperty("user.dir")+"/temp/code_output";
    /**
     * 应用部署目录
     *
     */
    String CODE_DEPLOY_ROOT_DIR=System.getProperty("user.dir")+"/temp/code_deploy";
    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST="http://localhost:8090";

    /**
     * 素材本地兜底存储目录（COS 上传失败时落盘到这里，由 MaterialResourceConfig 映射为
     * /api/material/file/** 静态访问）
     */
    String MATERIAL_ROOT_DIR=System.getProperty("user.dir")+"/temp/material";
}
