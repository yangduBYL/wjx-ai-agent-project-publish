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
}
