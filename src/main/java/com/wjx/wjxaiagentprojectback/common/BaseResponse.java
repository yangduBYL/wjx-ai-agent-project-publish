package com.wjx.wjxaiagentprojectback.common;

import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用返回结果，返回对应的json数据，包含状态码、数据、消息，用于统一处理业务逻辑
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {
    private int code;
    private T data;
    private String message;

    /**
     * 构造方法
     * @param code
     * @param data
     * @param message
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }
    public BaseResponse(int code, T data) {
        this.code = code;
        this.data = data;
    }
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
