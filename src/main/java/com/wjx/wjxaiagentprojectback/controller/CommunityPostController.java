package com.wjx.wjxaiagentprojectback.controller;

import com.mybatisflex.core.paginate.Page;
import com.wjx.wjxaiagentprojectback.annotation.RateLimit;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.DeleteRequest;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.RateLimitType;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityLikeResultVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityPostListVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityPostVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityStatsVO;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.service.CommunityPostService;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区帖子 控制层。
 *
 * <p>接口划分改写自个人博客系统 ArticleController：
 * 列表 / 详情 / 点赞 / 取消点赞 / 点赞状态保留，新增发布、更新、删除（作者或管理员）、
 * 「我的帖子」与社区统计。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/community/post")
public class CommunityPostController {

    @Resource
    private CommunityPostService communityPostService;

    @Resource
    private UserService userService;

    /**
     * 帖子分页列表（公开，未登录也可访问）
     *
     * @param queryRequest 查询条件（关键词、标签、作者、排序、分页）
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<CommunityPostListVO>> listPosts(
            @RequestBody(required = false) CommunityPostQueryRequest queryRequest,
            HttpServletRequest request) {
        return ResultUtils.success(communityPostService.listPosts(queryRequest, currentUserOrNull(request)));
    }

    /**
     * 我的帖子分页列表（含草稿，需登录）
     */
    @PostMapping("/list/page/mine")
    public BaseResponse<Page<CommunityPostListVO>> listMyPosts(
            @RequestBody(required = false) CommunityPostQueryRequest queryRequest,
            HttpServletRequest request) {
        CommunityPostQueryRequest query = queryRequest == null ? new CommunityPostQueryRequest() : queryRequest;
        query.setOnlyMine(true);
        return ResultUtils.success(communityPostService.listPosts(query, requireLogin(request)));
    }

    /**
     * 帖子详情（已发布帖子会自增浏览数；草稿仅作者/管理员可见）
     */
    @GetMapping("/get/{id}")
    public BaseResponse<CommunityPostVO> getPostDetail(@PathVariable("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAM_ERROR, "帖子 ID 无效");
        return ResultUtils.success(communityPostService.getPostDetail(id, currentUserOrNull(request)));
    }

    /**
     * 发布帖子
     */
    @PostMapping("/add")
    @RateLimit(key = "community_post_add", rate = 5, rateInterval = 60,
            limitType = RateLimitType.USER, message = "发帖过于频繁，请稍后再试")
    public BaseResponse<CommunityPostVO> addPost(@Valid @RequestBody CommunityPostAddRequest addRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAM_ERROR);
        return ResultUtils.success(communityPostService.addPost(addRequest, requireLogin(request)));
    }

    /**
     * 更新帖子（仅作者或管理员）
     */
    @PutMapping("/update")
    public BaseResponse<CommunityPostVO> updatePost(@Valid @RequestBody CommunityPostUpdateRequest updateRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(updateRequest == null, ErrorCode.PARAM_ERROR);
        return ResultUtils.success(communityPostService.updatePost(updateRequest, requireLogin(request)));
    }

    /**
     * 删除帖子（仅作者或管理员）
     */
    @DeleteMapping("/delete")
    public BaseResponse<Boolean> deletePost(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAM_ERROR, "帖子 ID 不能为空");
        communityPostService.deletePost(deleteRequest.getId(), requireLogin(request));
        return ResultUtils.success(true);
    }

    /**
     * 点赞
     */
    @PostMapping("/{id}/like")
    public BaseResponse<CommunityLikeResultVO> like(@PathVariable("id") Long id, HttpServletRequest request) {
        return ResultUtils.success(communityPostService.like(id, requireLogin(request)));
    }

    /**
     * 取消点赞
     */
    @DeleteMapping("/{id}/like")
    public BaseResponse<CommunityLikeResultVO> unlike(@PathVariable("id") Long id, HttpServletRequest request) {
        return ResultUtils.success(communityPostService.unlike(id, requireLogin(request)));
    }

    /**
     * 当前用户点赞状态（未登录返回 false，不抛未登录错误，便于列表页静默降级）
     */
    @GetMapping("/{id}/like/status")
    public BaseResponse<Boolean> likeStatus(@PathVariable("id") Long id, HttpServletRequest request) {
        User loginUser = currentUserOrNull(request);
        boolean liked = loginUser != null && communityPostService.hasLiked(id, loginUser.getId());
        return ResultUtils.success(liked);
    }

    /**
     * 社区统计（帖子数、今日新增、评论数、用户数）
     */
    @GetMapping("/stats")
    public BaseResponse<CommunityStatsVO> stats() {
        return ResultUtils.success(communityPostService.getStats());
    }

    /**
     * 获取当前登录用户，未登录返回 null（公开接口用）
     */
    private User currentUserOrNull(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户，未登录直接抛 40100（写接口用）
     */
    private User requireLogin(HttpServletRequest request) {
        User loginUser = currentUserOrNull(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        return loginUser;
    }
}
