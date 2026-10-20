package com.wjx.wjxaiagentprojectback.damon.dto.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发表社区评论 请求体。
 *
 * <p>改写自个人博客系统的 CommentRequest：articleId→postId，
 * 去掉游客 nickname/email（改为登录用户信息快照），保留 parentId 支持盖楼回复。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class CommunityCommentAddRequest implements Serializable {

    /**
     * 帖子 ID
     */
    @NotNull(message = "帖子 ID 不能为空")
    private Long postId;

    /**
     * 父评论 ID（回复某条评论时填写）
     */
    private Long parentId;

    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最多 1000 字")
    private String content;
}
