package com.wjx.wjxaiagentprojectback.exception;

/**
 * 抛出异常的工具类
 *
 * ErrorCode：错误码（提供自定义错误码）
 * BusinessException：业务异常（提供自定义业务异常）
 * throwIf：执行工具类，调用businessException抛出异常。如果条件为真，则抛出异常（提供自定义异常抛出）
 */
public class ThrowUtils {
    /**
     * 如果条件为真，则抛出运行时异常
     * @param condition
     * @param runtimeException
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException){
        if(condition){
            throw runtimeException;
        }
    }

    /**
     * 如果条件为真，则抛出状态码+信息异常
     * @param condition
     * @param code
     * @param message
     */
    public static void throwIf(boolean condition, int code,String message){
        if(condition){
            throw new BusinessException(code, message);
        }
    }
    /**
     * 如果条件为真，则抛出自定义异常
     * @param condition
     * @param errorCode
     */
    public static void throwIf(boolean condition, ErrorCode errorCode){
        if(condition){
            throw new BusinessException(errorCode);
        }
    /**
     * 如果条件为真，则抛异常自定义和信息
     * @param condition
     * @param errorCode
     * @param message
     */
    }
    public static void throwIf(boolean condition, ErrorCode errorCode, String message){
        if(condition){
            throw new BusinessException(errorCode, message);
        }
    }
}
