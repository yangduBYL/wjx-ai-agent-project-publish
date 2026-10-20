package com.wjx.wjxaiagentprojectback.damon.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {
    //用户账号
    private String userAccount;
    //用户密码
    private String userPassword;
}
