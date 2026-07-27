package com.wjx.wjxaiagentprojectback.damon.vo;

import com.wjx.wjxaiagentprojectback.damon.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data

public class App_UserVo {
    //app信息
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
    //用户信息
    private UserVo uservo;
}
