package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区评论 VO（对应个人博客系统的 CommentVO，保留两级盖楼结构）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class CommunityCommentVO implements Serializable {

    /**
     * 评论 ID
     */
    private Long id;

    /**
     * 帖子 ID
     */
    private Long postId;

    /**
     * 父评论 ID
     */
    private Long parentId;

    /**
     * 评论者用户 ID
     */
    private Long authorId;

    /**
     * 评论者昵称
     */
    private String nickname;

    /**
     * 评论者头像
     */
    private String avatar;

    /**
     * 被回复者昵称（回复二级评论时便于阅读）
     */
    private String replyToName;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 是否由当前登录用户发布（前端控制删除按钮）
     */
    private Boolean mine;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 二级回复列表
     */
    private List<CommunityCommentVO> replies;
}
