package com.wjx.wjxaiagentprojectback.damon.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 序列化保存到硬盘不出问题。
 */
@Data
public class UserRegisterRequest implements Serializable {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 账号密码
     */
    private String userPassword;
    /**
     * 确认密码
     */
    private String checkPassword;
}
