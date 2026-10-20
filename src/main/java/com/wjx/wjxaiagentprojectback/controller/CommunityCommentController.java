package com.wjx.wjxaiagentprojectback.controller;

import com.wjx.wjxaiagentprojectback.annotation.RateLimit;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.DeleteRequest;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityCommentAddRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.RateLimitType;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityCommentVO;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.service.CommunityCommentService;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区评论 控制层。
 *
 * <p>改写自个人博客系统 CommentController：列表 / 发表保留，
 * 新增删除接口；发表接口沿用博客的 @RateLimit 思路（本项目限流注解参数不同），
 * 并要求登录后才能评论（博客是游客填写昵称即可）。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/community/comment")
public class CommunityCommentController {

    @Resource
    private CommunityCommentService communityCommentService;

    @Resource
    private UserService userService;

    /**
     * 查询帖子评论树（公开）
     */
    @GetMapping("/list")
    public BaseResponse<List<CommunityCommentVO>> list(@RequestParam("postId") Long postId,
                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAM_ERROR, "帖子 ID 无效");
        return ResultUtils.success(communityCommentService.listByPostId(postId, currentUserOrNull(request)));
    }

    /**
     * 发表评论 / 回复（需登录，每 60 秒最多 3 条，防刷屏）
     */
    @PostMapping("/add")
    @RateLimit(key = "community_comment_add", rate = 3, rateInterval = 60,
            limitType = RateLimitType.USER, message = "评论过于频繁，请稍后再试")
    public BaseResponse<CommunityCommentVO> add(@Valid @RequestBody CommunityCommentAddRequest addRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAM_ERROR);
        User loginUser = requireLogin(request);
        return ResultUtils.success(communityCommentService.addComment(addRequest, loginUser, request));
    }

    /**
     * 删除评论（评论作者或管理员）
     */
    @DeleteMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAM_ERROR, "评论 ID 不能为空");
        communityCommentService.deleteComment(deleteRequest.getId(), requireLogin(request));
        return ResultUtils.success(true);
    }

    private User currentUserOrNull(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (BusinessException e) {
            return null;
        }
    }

    private User requireLogin(HttpServletRequest request) {
        User loginUser = currentUserOrNull(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        return loginUser;
    }
}
