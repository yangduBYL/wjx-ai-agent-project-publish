package com.wjx.wjxaiagentprojectback.damon.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新请求类
 * 用于封装前端传递的用户更新信息
 */
@Data
    // 这是一个空的类定义，后续可以添加用户更新所需的属性和方法
public class UserUpdateRequest implements Serializable {
    private Long id;
    private String userName;
    private String userAccount;
    private String userAvatar;
    private String userRole;
    private String userProfile;
}
