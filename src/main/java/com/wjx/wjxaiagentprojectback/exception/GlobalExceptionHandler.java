package com.wjx.wjxaiagentprojectback.exception;

import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Hidden//防止swagger显示
@RestControllerAdvice
/**
 * @RestControllerAdvice 是 Spring 框架中用于统一处理 Web 层异常和封装返回结果的核心注解。简单来说，它就是 @ControllerAdvice + @ResponseBody 的组合体。
 * 通过使用 @RestControllerAdvice，我们可以集中处理项目中抛出的异常，而不需要在每个 Controller 中重复编写异常处理逻辑。
 * 同时，结合 @ExceptionHandler 注解，我们可以为不同的异常指定不同的处理方法，从而实现细粒度的异常处理。
 * 最后，通过返回 BaseResponse 对象，我们可以统一定义返回结果的格式，包括状态码、消息和数据等信息。
 */
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 1.定义多个异常处理方法，拦截整个项目中特定类型（或全部）的异常，并统一返回 JSON 格式的错误信息，而无需在每个 Controller 里写 try-catch
     * 2。@ExceptionHandler 配合 @RestControllerAdvice，可以避免在每个方法上都加 @ResponseBody，确保所有异常响应都是 JSON 格式，非常适合前后端分离架构
     */
    /**
     * 业务异常处理（自定义错误）
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("{}", e.getMessage());// 后端打印错误信息
        //返回给前端对应的错误信息响应
        return ResultUtils.error(e.getCode(), e.getMessage());

    }
    /**
     * 运行时异常处理(系统错误)
     * @param e
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("{}", e.getMessage());// 后端打印错误信息
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");// 返回给前端对应的错误信息响应
    }
}
