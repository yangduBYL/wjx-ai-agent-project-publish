package com.wjx.wjxaiagentprojectback.config;

import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 素材本地兜底存储的静态资源映射。
 *
 * <p>对齐个人博客系统 WebConfig 的 /uploads/** 映射：
 * COS 上传失败时图片落盘到 temp/material/{userId}/，通过
 * /api/material/file/{userId}/{fileName} 访问。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Slf4j
@Configuration
public class MaterialResourceConfig implements WebMvcConfigurer {

    private Path materialRoot;

    @PostConstruct
    public void init() {
        materialRoot = Paths.get(AppConstant.MATERIAL_ROOT_DIR).toAbsolutePath().normalize();
        try {
            Files.createDirectories(materialRoot);
            log.info("素材本地存储目录: {}", materialRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建素材存储目录: " + materialRoot, e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = materialRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        // PathResourceResolver 默认禁止越出根目录，无需额外做路径穿越校验
        registry.addResourceHandler("/material/file/**")
                .addResourceLocations(location);
    }
}
