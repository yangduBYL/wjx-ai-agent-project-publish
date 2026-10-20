package com.wjx.wjxaiagentprojectback.exception;

import lombok.Getter;

/**
 * 枚举异常码
 */
@Getter
public enum ErrorCode {
    SUCCESS(0,"OK"),
    PARAM_ERROR(40000,"请求参数错误"),
    NOT_LOGIN_ERROR(40100,"未登录"),
    NOT_AUTH_ERROR(40101,"无权限"),
    NOT_FOUND(40400,"资源不存在"),
    FORBIDDEN_ERROR(40300,"禁止访问"),
    SYSTEM_ERROR(50000,"系统错误"),
    OPERATION_ERROR(50001,"操作错误"),
    INTERNAL_SERVER_ERROR(500,"服务器错误"),
    TOO_MANY_REQUEST(42900, "请求过于频繁");

    /**
     * 状态码
     */
    private final  int code;
    /**
     * 信息
     */
    private final String message;
    /**
     * 私有构造函数
     * @param code
     * @param message
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
