package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录返回前端数据
 *
 *
 */
@Data
public class userLoginRequestVo {
    private long id;
    private String userAccount;
    private String userName;
    private String userAvatar;
    private String userRole;
    //用户简介
    private String userProfile;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
