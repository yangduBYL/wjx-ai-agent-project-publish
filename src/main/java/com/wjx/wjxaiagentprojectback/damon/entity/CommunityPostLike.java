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
 * 社区帖子点赞记录 实体类。
 *
 * <p>个人博客系统用 Redis Set（article:like:{id} + 客户端 IP）做点赞去重，
 * 本项目要求登录后才能点赞，因此改为持久化点赞表 + (postId, userId) 唯一索引：
 * 点赞状态可稳定反查、点赞数与记录不会因 Redis 过期而不一致。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("community_post_like")
public class CommunityPostLike implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 点赞记录 ID（雪花算法）
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 帖子 ID
     */
    @Column("postId")
    private Long postId;

    /**
     * 点赞用户 ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 点赞时间
     */
    @Column("createTime")
    private LocalDateTime createTime;
}
