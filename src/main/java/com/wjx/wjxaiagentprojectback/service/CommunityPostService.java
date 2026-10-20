package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPost;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityLikeResultVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityPostListVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityPostVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityStatsVO;

/**
 * 社区帖子 服务层。
 *
 * <p>接口职责对齐个人博客系统的 ArticleService：
 * 列表分页、详情（含浏览数自增）、发布、更新、删除、点赞/取消点赞/点赞状态、统计。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityPostService extends IService<CommunityPost> {

    /**
     * 分页查询帖子（公开列表只返回已发布；onlyMine 时返回当前用户全部帖子）
     *
     * @param queryRequest 查询条件
     * @param loginUser    当前登录用户（可为空）
     * @return 分页结果
     */
    Page<CommunityPostListVO> listPosts(CommunityPostQueryRequest queryRequest, User loginUser);

    /**
     * 帖子详情（已发布帖子会自增浏览数）
     *
     * @param id        帖子 ID
     * @param loginUser 当前登录用户（可为空）
     * @return 详情
     */
    CommunityPostVO getPostDetail(Long id, User loginUser);

    /**
     * 发布帖子
     */
    CommunityPostVO addPost(CommunityPostAddRequest addRequest, User loginUser);

    /**
     * 更新帖子（仅作者或管理员）
     */
    CommunityPostVO updatePost(CommunityPostUpdateRequest updateRequest, User loginUser);

    /**
     * 删除帖子（仅作者或管理员，物理删除并级联清理标签/评论/点赞）
     */
    void deletePost(Long id, User loginUser);

    /**
     * 点赞
     */
    CommunityLikeResultVO like(Long postId, User loginUser);

    /**
     * 取消点赞
     */
    CommunityLikeResultVO unlike(Long postId, User loginUser);

    /**
     * 查询当前用户是否已点赞
     */
    boolean hasLiked(Long postId, Long userId);

    /**
     * 社区统计
     */
    CommunityStatsVO getStats();
}
