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
 * 社区评论 实体类。
 *
 * <p>改写自个人博客系统的 comment 表：保留 postId/parentId/content/ip/userAgent 结构，
 * 把游客昵称 + 邮箱改为登录用户 authorId，并快照 nickname/avatar。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("community_comment")
public class CommunityComment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评论 ID（雪花算法）
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 帖子 ID
     */
    @Column("postId")
    private Long postId;

    /**
     * 父评论 ID（为空表示一级评论）
     */
    @Column("parentId")
    private Long parentId;

    /**
     * 评论者用户 ID
     */
    @Column("authorId")
    private Long authorId;

    /**
     * 评论者昵称快照
     */
    private String nickname;

    /**
     * 评论者头像快照
     */
    private String avatar;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论者 IP
     */
    private String ip;

    /**
     * 浏览器 UA
     */
    @Column("userAgent")
    private String userAgent;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 是否删除（逻辑删除）
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
