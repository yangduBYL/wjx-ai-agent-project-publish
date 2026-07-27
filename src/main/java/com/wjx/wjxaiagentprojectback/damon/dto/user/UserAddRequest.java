package com.wjx.wjxaiagentprojectback.damon.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
/**
 * @author wjx
 * @date 2026/8/19 15:58
 */
public class UserAddRequest implements Serializable {
    private String userName;
    private String userAccount;
    private String userAvatar;
    private String userRole;
    private String userProfile;
}
