package com.wjx.wjxaiagentprojectback.utils;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 工具类。
 *
 * <p>改写自个人博客系统 IpUtil，增加空请求兜底（评论接口也会采集 IP 用于风控排查）。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public class ClientIpUtil {

    private ClientIpUtil() {
    }

    /**
     * 获取真实客户端 IP（兼容 Nginx 等反向代理）
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
