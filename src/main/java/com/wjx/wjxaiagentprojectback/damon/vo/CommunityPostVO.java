package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区帖子详情 VO（对应个人博客系统的 ArticleVO）。
 *
 * <p>额外聚合了作者昵称头像快照、关联应用信息、标签、当前用户是否已点赞。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class CommunityPostVO implements Serializable {

    /**
     * 帖子 ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文
     */
    private String content;

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
     * 关联应用名称（分享作品时展示）
     */
    private String appName;

    /**
     * 关联应用封面
     */
    private String appCover;

    /**
     * 关联应用代码生成类型（前端拼预览地址用）
     */
    private String appCodeGenType;

    /**
     * 关联应用部署标识（有值表示已部署，可直接访问）
     */
    private String appDeployKey;

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
     * 当前登录用户是否已点赞（未登录恒为 false）
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

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
