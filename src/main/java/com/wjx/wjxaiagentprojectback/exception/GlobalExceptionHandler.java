package com.wjx.wjxaiagentprojectback.exception;

import cn.hutool.json.JSONUtil;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

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
        log.error("BusinessException", e);
        // 尝试处理 SSE 请求
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        // 对于普通请求，返回标准 JSON 响应
        return ResultUtils.error(e.getCode(), e.getMessage());
    }
    /**
     * 运行时异常处理(系统错误)
     * @param e
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        // 尝试处理 SSE 请求
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 参数校验异常处理（@Valid / @Validated 触发的字段校验失败）
     * 不拦截的话会落到 RuntimeException 分支，统一返回 50000「系统错误」，
     * 前端看不到「标题不能为空」这类具体提示。
     *
     * @param e 校验异常
     * @return 40000 + 第一条字段错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败：{}", message);
        return ResultUtils.error(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 请求体缺失/格式错误（如 @RequestBody 必填但传了空体）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAM_ERROR, "请求参数格式错误");
    }

    /**
     * 处理SSE请求的错误响应
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return true表示是SSE请求并已处理，false表示不是SSE请求
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        // 判断是否是SSE请求（通过Accept头或URL路径）
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        if ((accept != null && accept.contains("text/event-stream")) ||
                uri.contains("/chat/gen/code")) {
            try {
                // 设置SSE响应头
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                // 构造错误消息的SSE格式
                Map<String, Object> errorData = Map.of(
                        "error", true,
                        "code", errorCode,
                        "message", errorMessage
                );
                String errorJson = JSONUtil.toJsonStr(errorData);
                // 发送业务错误事件（避免与标准error事件冲突）
                String sseData = "event: business-error\ndata: " + errorJson + "\n\n";
                response.getWriter().write(sseData);
                response.getWriter().flush();
                // 发送结束事件
                response.getWriter().write("event: done\ndata: {}\n\n");
                response.getWriter().flush();
                // 关键：把响应刷成 committed 状态。
                // 否则 @ExceptionHandler 方法返回 null 后，Spring 仍会拿返回值去走消息转换器，
                // 而请求头 Accept: text/event-stream 无法被 BaseResponse 的 JSON 转换器满足，
                // 会抛 HttpMediaTypeNotAcceptableException → 客户端只拿到 500 + 空响应体，
                // 前端永远收不到 business-error 事件（2026-10-16 实测复现并修复）。
                // ServletInvocableHandlerMethod 里判断到 response.isCommitted() 时会直接 setRequestHandled(true)，
                // 不再做消息转换。
                response.flushBuffer();
                // 表示已处理SSE请求
                return true;
            } catch (IOException ioException) {
                log.error("Failed to write SSE error response", ioException);
                // 即使写入失败，也表示这是SSE请求
                return true;
            }
        }
        return false;
    }
}