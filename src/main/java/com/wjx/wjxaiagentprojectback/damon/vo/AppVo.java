package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
@Data
public class AppVo implements Serializable {
    private Long id;
    private String appName;
    private String cover;
    private String initprompt;
    private String codeGenType;
    private String deployKey;
    private LocalDateTime deployedTime;
    private Integer priority;
    private Long userId;
    private LocalDateTime createTime;

}
