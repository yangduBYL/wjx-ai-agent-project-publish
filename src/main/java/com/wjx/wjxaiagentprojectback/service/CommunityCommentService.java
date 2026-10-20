package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.service.IService;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityCommentAddRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityComment;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityCommentVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 社区评论 服务层（对应个人博客系统的 CommentService）。
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityCommentService extends IService<CommunityComment> {

    /**
     * 查询帖子下的评论树（一级评论 + 二级回复）
     *
     * @param postId    帖子 ID
     * @param loginUser 当前登录用户（可为空，用于标记 mine）
     */
    List<CommunityCommentVO> listByPostId(Long postId, User loginUser);

    /**
     * 发表评论 / 回复
     */
    CommunityCommentVO addComment(CommunityCommentAddRequest addRequest, User loginUser, HttpServletRequest request);

    /**
     * 删除评论（评论作者或管理员），并同步减少帖子评论数
     */
    void deleteComment(Long id, User loginUser);
}
