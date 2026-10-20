package com.wjx.wjxaiagentprojectback.damon.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 帖子-标签关联 实体类（联合主键，仅用于 BaseMapper 映射，写入/删除走 Mapper 自定义 SQL）。
 *
 * <p>改写自个人博客系统的 article_tag 表。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("community_post_tag")
public class CommunityPostTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 帖子 ID
     */
    @Id
    @Column("postId")
    private Long postId;

    /**
     * 标签 ID
     */
    @Column("tagId")
    private Long tagId;
}
