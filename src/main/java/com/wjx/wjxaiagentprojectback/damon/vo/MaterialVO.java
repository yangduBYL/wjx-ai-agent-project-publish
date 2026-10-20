package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 素材 VO（对应个人博客系统上传接口返回的 { url }，这里补全列表展示所需字段）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class MaterialVO implements Serializable {

    /**
     * 素材 ID
     */
    private Long id;

    /**
     * 访问地址（可直接用作帖子封面 / img src）
     */
    private String url;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * MIME 类型
     */
    private String contentType;

    /**
     * 文件大小（字节，Long 会序列化成字符串）
     */
    private Long size;

    /**
     * 上传时间
     */
    private LocalDateTime createTime;
}
