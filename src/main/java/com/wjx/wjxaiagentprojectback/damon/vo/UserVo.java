package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserVo implements Serializable {
    private Long id;
    private String userName;
    private String userAccount;
    private String userAvatar;
    private String userRole;
    private String userProfile;
    private LocalDateTime createTime;
}
