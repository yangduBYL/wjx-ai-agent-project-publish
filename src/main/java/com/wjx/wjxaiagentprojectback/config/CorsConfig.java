package com.wjx.wjxaiagentprojectback.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局配置跨域资源共享
 * WebMvc配置类,用于配置跨域请求
 */

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        //工厂添加mapping拦截/** 表示拦截项目中所有的请求路径（包括子路径）。这意味着该配置对所有的 Controller 接口都生效。
        registry.addMapping("/**")
                //allowCredentials(true)
                //含义：是否允许浏览器携带身份凭证（Credentials）可以携带 Cookies、HTTP 认证信息
                .allowCredentials(true)
                //.allowedOriginPatterns("*") （重点）
                //含义：允许哪些外部域名的请求进行跨域访问
                .allowedOriginPatterns("*")
                //.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                //含义：允许前端使用哪些 HTTP 方法（动词）发起跨域请求。
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                //.allowedHeaders("*")
                //含义：允许前端在请求头（Request Headers）中携带哪些自定义字段。
                //令牌或自定义的 token 字段，都不会被浏览器拦截
                .allowedHeaders("*")
                //.exposedHeaders("*")
                //含义：允许前端 JavaScript 代码（如 Axios、Fetch）从响应头（Response Headers）中读取哪些自定义字段。
                //默认情况下，浏览器只允许 JS 访问简单的响应头（如 Content-Type、Content-Length）。
                //
                //如果你想在前端获取后端返回的自定义响应头（例如 X-Total-Count 用于分页，或 Authorization 刷新令牌），就必须在这里暴露出来。
                .exposedHeaders("*");
    }
}
