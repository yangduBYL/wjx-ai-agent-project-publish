package com.wjx.wjxaiagentprojectback.damon.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 素材（用户上传的图片） 实体类。
 *
 * <p>改写自个人博客系统的 media 表：保留 url/originalName/size/createTime，
 * 新增 userId（按用户隔离）、contentType、storagePath（删除时清理存储）、isDelete（逻辑删除）。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("material")
public class Material implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 素材 ID（雪花算法）
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 上传用户 ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 访问地址（COS 公网地址，或本地兜底地址 /api/material/file/...）
     */
    private String url;

    /**
     * 存储定位：COS 对象键或本地相对路径
     */
    @Column("storagePath")
    private String storagePath;

    /**
     * 原始文件名
     */
    @Column("originalName")
    private String originalName;

    /**
     * MIME 类型
     */
    @Column("contentType")
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 上传时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 是否删除（逻辑删除）
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
