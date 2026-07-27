package com.wjx.wjxaiagentprojectback.damon.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用部署请求
 */
@Data
public class AppDeployRequest implements Serializable {
    private Long appId;
}
