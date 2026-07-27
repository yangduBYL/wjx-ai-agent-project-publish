package com.wjx.wjxaiagentprojectback.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException{
    /**
     * 状态码
     */
    private final int code;
    /**
     * 信息
     */
    private final String message;
    /**
     * 私有构造函数
     * @param code
     * @param message
     */
    public BusinessException(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据枚举类构造异常对象
     * @param errorCode
     */
    public BusinessException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    /**
     * 根据枚举类构造异常对象
     * @param errorCode
     * @param message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        this.code = errorCode.getCode();
        this.message = message;
    }
}
