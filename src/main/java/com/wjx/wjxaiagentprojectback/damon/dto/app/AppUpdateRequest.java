package com.wjx.wjxaiagentprojectback.damon.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AppUpdateRequest implements Serializable {
    private Long id;
    /**
     * 应用名称
     */

    private String appName;
    /**
     * 应用封面
     */
    private String cover;
    /**
     * 应用初始化提示
     */
    private String initprompt;
    /**
     * 代码生成类型
     */

    private String codeGenType;

    /**
     * 部署标识
     */

    private String deployKey;

    /**
     * 部署时间
     */

    private LocalDateTime deployedTime;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 用户id
     */

    private Long userId;

}
