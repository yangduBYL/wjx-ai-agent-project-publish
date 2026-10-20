package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区帖子列表项 VO（对应个人博客系统的 ArticleListVO，不含正文）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class CommunityPostListVO implements Serializable {

    /**
     * 帖子 ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 封面
     */
    private String cover;

    /**
     * 关联应用 ID
     */
    private Long appId;

    /**
     * 关联应用名称
     */
    private String appName;

    /**
     * 作者用户 ID
     */
    private Long authorId;

    /**
     * 作者昵称
     */
    private String authorName;

    /**
     * 作者头像
     */
    private String authorAvatar;

    /**
     * 状态：0 草稿 1 已发布
     */
    private Integer status;

    /**
     * 浏览数
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 当前登录用户是否已点赞
     */
    private Boolean liked;

    /**
     * 当前登录用户是否为作者
     */
    private Boolean mine;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
