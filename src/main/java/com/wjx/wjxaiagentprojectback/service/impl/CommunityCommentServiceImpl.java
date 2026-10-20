package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityCommentAddRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityComment;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPost;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityCommentVO;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.mapper.CommunityCommentMapper;
import com.wjx.wjxaiagentprojectback.mapper.CommunityPostMapper;
import com.wjx.wjxaiagentprojectback.service.CommunityCommentService;
import com.wjx.wjxaiagentprojectback.utils.ClientIpUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 社区评论 服务层实现。
 *
 * <p>改写自个人博客系统 CommentService：
 * <ul>
 *   <li>listByArticleId 的「一级评论 + 按 parentId 分组的两级盖楼」逻辑原样保留</li>
 *   <li>create 里的游客 nickname/email + IP/UA 采集，改为登录用户快照 + IP/UA 采集</li>
 *   <li>新增删除评论（作者/管理员）并同步维护帖子 commentCount</li>
 * </ul>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Slf4j
@Service
public class CommunityCommentServiceImpl extends ServiceImpl<CommunityCommentMapper, CommunityComment>
        implements CommunityCommentService {

    /**
     * 已发布状态
     */
    private static final int STATUS_PUBLISHED = 1;

    @Resource
    private CommunityPostMapper communityPostMapper;

    @Override
    public List<CommunityCommentVO> listByPostId(Long postId, User loginUser) {
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAM_ERROR, "帖子 ID 无效");
        List<CommunityComment> comments = this.list(QueryWrapper.create()
                .eq("postId", postId)
                .orderBy("createTime", true));
        if (comments.isEmpty()) {
            return List.of();
        }
        // 1. 先按 id 建立索引，便于取「被回复人昵称」
        Map<Long, CommunityComment> byId = comments.stream()
                .collect(Collectors.toMap(CommunityComment::getId, c -> c, (a, b) -> a));
        // 2. 二级回复按 parentId 分组（与博客一致的盖楼方式）
        Map<Long, List<CommunityCommentVO>> repliesMap = new HashMap<>();
        for (CommunityComment comment : comments) {
            if (comment.getParentId() != null) {
                CommunityCommentVO replyVO = toVO(comment, loginUser);
                CommunityComment parent = byId.get(comment.getParentId());
                replyVO.setReplyToName(parent == null ? null : parent.getNickname());
                repliesMap.computeIfAbsent(comment.getParentId(), k -> new ArrayList<>()).add(replyVO);
            }
        }
        // 3. 组装一级评论
        List<CommunityCommentVO> roots = new ArrayList<>();
        for (CommunityComment comment : comments) {
            if (comment.getParentId() == null) {
                CommunityCommentVO vo = toVO(comment, loginUser);
                vo.setReplies(repliesMap.getOrDefault(comment.getId(), List.of()));
                roots.add(vo);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityCommentVO addComment(CommunityCommentAddRequest addRequest, User loginUser,
                                         HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录后再评论");
        ThrowUtils.throwIf(StrUtil.isBlank(addRequest.getContent()), ErrorCode.PARAM_ERROR, "评论内容不能为空");
        ThrowUtils.throwIf(addRequest.getContent().trim().length() > 1000, ErrorCode.PARAM_ERROR, "评论最多 1000 字");

        // 1. 帖子必须存在且已发布
        CommunityPost post = communityPostMapper.selectOneById(addRequest.getPostId());
        ThrowUtils.throwIf(post == null || !isPublished(post.getStatus()),
                ErrorCode.NOT_FOUND, "帖子不存在或已下架");

        // 2. 校验父评论：必须属于同一帖子，且只保留两级（回复二级评论时挂到一级评论下）
        Long parentId = addRequest.getParentId();
        String replyToName = null;
        if (parentId != null) {
            CommunityComment parent = this.getById(parentId);
            ThrowUtils.throwIf(parent == null || !post.getId().equals(parent.getPostId()),
                    ErrorCode.PARAM_ERROR, "被回复的评论不存在");
            replyToName = parent.getNickname();
            parentId = parent.getParentId() == null ? parent.getId() : parent.getParentId();
        }

        // 3. 保存评论（昵称/头像做快照，避免用户改名后历史评论错乱）
        CommunityComment comment = CommunityComment.builder()
                .postId(post.getId())
                .parentId(parentId)
                .authorId(loginUser.getId())
                .nickname(resolveNickname(loginUser))
                .avatar(loginUser.getUserAvatar())
                .content(addRequest.getContent().trim())
                .ip(ClientIpUtil.getClientIp(request))
                .userAgent(StrUtil.sub(request == null ? null : request.getHeader("User-Agent"), 0, 500))
                .createTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        this.save(comment);
        // 4. 帖子评论数 +1
        communityPostMapper.updateCommentCount(post.getId(), 1);

        CommunityCommentVO vo = toVO(comment, loginUser);
        vo.setReplyToName(replyToName);
        vo.setReplies(List.of());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAM_ERROR, "评论 ID 无效");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        CommunityComment comment = this.getById(id);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND, "评论不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !loginUser.getId().equals(comment.getAuthorId()),
                ErrorCode.NOT_AUTH_ERROR, "无权限删除该评论");

        // 物理删除评论及其直接子回复，并按实际删除条数回写帖子评论数
        int deleted = this.mapper.physicalDeleteWithReplies(id);
        if (deleted > 0) {
            communityPostMapper.updateCommentCount(comment.getPostId(), -deleted);
        }
    }

    /**
     * 已发布判断（拆成方法避免拆箱 NPE）
     */
    private boolean isPublished(Integer status) {
        return status != null && status == STATUS_PUBLISHED;
    }

    /**
     * 昵称兜底：昵称为空时用账号
     */
    private String resolveNickname(User user) {
        String nickname = StrUtil.blankToDefault(user.getUserName(), user.getUserAccount());
        return StrUtil.sub(nickname, 0, 50);
    }

    /**
     * 实体转 VO，并标记是否为当前登录用户发布
     */
    private CommunityCommentVO toVO(CommunityComment comment, User loginUser) {
        CommunityCommentVO vo = new CommunityCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setAuthorId(comment.getAuthorId());
        vo.setNickname(comment.getNickname());
        vo.setAvatar(comment.getAvatar());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());
        vo.setMine(loginUser != null && loginUser.getId().equals(comment.getAuthorId()));
        return vo;
    }
}
