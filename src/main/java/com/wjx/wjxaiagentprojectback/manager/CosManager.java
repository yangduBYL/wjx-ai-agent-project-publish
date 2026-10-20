package com.wjx.wjxaiagentprojectback.manager;


import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import cn.hutool.core.util.StrUtil;
import com.wjx.wjxaiagentprojectback.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 通用业务处理层
 * COS对象存储管理器
 *
 * @author wjx
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        // 上传文件
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            // 构建访问URL：host 可能带/不带结尾斜杠，key 可能带/不带开头斜杠，统一拼成 host/key
            String host = StrUtil.removeSuffix(cosClientConfig.getHost(), "/");
            String objectKey = key.startsWith("/") ? key : "/" + key;
            String url = host + objectKey;
            log.info("文件上传COS成功: {} -> {}", file.getName(), url);
            return url;
        } else {
            log.error("文件上传COS失败，返回结果为空");
            return null;
        }
    }

    /**
     * 删除 COS 对象（素材删除时清理存储用）
     *
     * @param key COS对象键（完整路径）
     */
    public void deleteObject(String key) {
        try {
            cosClient.deleteObject(cosClientConfig.getBucket(), key);
            log.info("COS 对象删除成功: {}", key);
        } catch (Exception e) {
            log.warn("COS 对象删除失败: {} -> {}", key, e.getMessage());
        }
    }
}
