package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 社区统计 VO（对应个人博客系统的 ArticleStatsVO）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityStatsVO implements Serializable {

    /**
     * 已发布帖子总数
     */
    private Long postTotal;

    /**
     * 今日新增帖子数
     */
    private Long todayTotal;

    /**
     * 评论总数
     */
    private Long commentTotal;

    /**
     * 注册用户数
     */
    private Long userTotal;
}
