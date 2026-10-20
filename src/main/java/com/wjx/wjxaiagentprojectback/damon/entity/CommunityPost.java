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
 * 社区帖子 实体类。
 *
 * <p>改写自个人博客系统的 article 表（见 F:\Blog-project\blog-platform\blog-server）：
 * title/content/excerpt/cover/status/viewCount/likeCount/authorId 沿用其设计，
 * 去掉 slug（详情改用雪花 ID 路由）、新增 appId 用于关联「AI 生成的应用」，
 * 新增 authorName/authorAvatar 昵称头像快照、commentCount 评论计数。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("community_post")
public class CommunityPost implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 帖子 ID（雪花算法，前端按字符串处理）
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文（纯文本 / Markdown 源码）
     */
    private String content;

    /**
     * 摘要（为空时由正文自动截取）
     */
    private String excerpt;

    /**
     * 封面图地址
     */
    private String cover;

    /**
     * 关联的 AI 生成应用 ID（分享作品时填写，可为空）
     */
    @Column("appId")
    private Long appId;

    /**
     * 作者用户 ID
     */
    @Column("authorId")
    private Long authorId;

    /**
     * 作者昵称快照
     */
    @Column("authorName")
    private String authorName;

    /**
     * 作者头像快照
     */
    @Column("authorAvatar")
    private String authorAvatar;

    /**
     * 状态：0 草稿 1 已发布
     */
    private Integer status;

    /**
     * 浏览数
     */
    @Column("viewCount")
    private Integer viewCount;

    /**
     * 点赞数
     */
    @Column("likeCount")
    private Integer likeCount;

    /**
     * 评论数
     */
    @Column("commentCount")
    private Integer commentCount;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除（逻辑删除）
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
