package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.community.CommunityPostUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.App;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPost;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPostLike;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityLikeResultVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityPostListVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityPostVO;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityStatsVO;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.mapper.CommunityCommentMapper;
import com.wjx.wjxaiagentprojectback.mapper.CommunityPostLikeMapper;
import com.wjx.wjxaiagentprojectback.mapper.CommunityPostMapper;
import com.wjx.wjxaiagentprojectback.mapper.CommunityPostTagMapper;
import com.wjx.wjxaiagentprojectback.service.AppService;
import com.wjx.wjxaiagentprojectback.service.CommunityPostService;
import com.wjx.wjxaiagentprojectback.service.CommunityTagService;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区帖子 服务层实现。
 *
 * <p>改写自个人博客系统 ArticleService，保留其核心业务流：
 * <ul>
 *   <li>listPublished：已发布 + 标签过滤 + 分页</li>
 *   <li>getBySlug：详情 + 浏览数自增（本项目改为按 ID 查询）</li>
 *   <li>create/update/delete：写正文、生成摘要、维护标签、删帖级联清理评论</li>
 *   <li>like/unlike/hasLiked：点赞去重与计数</li>
 *   <li>getStats：社区统计</li>
 * </ul>
 * 差异点：MyBatis-Plus → MyBatis-Flex；Result → BaseResponse（Controller 层包装）；
 * 点赞由 Redis Set + IP 改为 DB 点赞表 + 登录用户；浏览数改为数据库原子自增；
 * 新增 appId 关联 AI 生成应用、帖子/评论作者校验。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Slf4j
@Service
public class CommunityPostServiceImpl extends ServiceImpl<CommunityPostMapper, CommunityPost>
        implements CommunityPostService {

    /**
     * 草稿状态
     */
    private static final int STATUS_DRAFT = 0;

    /**
     * 已发布状态
     */
    private static final int STATUS_PUBLISHED = 1;

    /**
     * 单页最大条数，避免恶意大分页
     */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 自动摘要长度
     */
    private static final int EXCERPT_LENGTH = 150;

    /**
     * 允许排序的字段白名单（防止 sortField 注入）
     */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createTime", "likeCount", "viewCount", "commentCount");

    @Resource
    private CommunityTagService communityTagService;

    @Resource
    private CommunityPostLikeMapper communityPostLikeMapper;

    @Resource
    private CommunityPostTagMapper communityPostTagMapper;

    @Resource
    private CommunityCommentMapper communityCommentMapper;

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    @Override
    public Page<CommunityPostListVO> listPosts(CommunityPostQueryRequest queryRequest, User loginUser) {
        CommunityPostQueryRequest query = queryRequest == null ? new CommunityPostQueryRequest() : queryRequest;
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);

        Page<CommunityPost> postPage = this.page(Page.of(pageNum, pageSize), buildListWrapper(query, loginUser));
        Page<CommunityPostListVO> result = new Page<>(pageNum, pageSize, postPage.getTotalRow());
        List<CommunityPost> records = postPage.getRecords();
        if (CollUtil.isEmpty(records)) {
            result.setRecords(List.of());
            return result;
        }

        Map<Long, App> appMap = loadApps(records);
        Set<Long> likedPostIds = loadLikedPostIds(records, loginUser);
        List<CommunityPostListVO> list = new ArrayList<>(records.size());
        for (CommunityPost post : records) {
            list.add(toListVO(post, appMap, likedPostIds, loginUser));
        }
        result.setRecords(list);
        return result;
    }

    @Override
    public CommunityPostVO getPostDetail(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAM_ERROR, "帖子 ID 无效");
        CommunityPost post = this.getById(id);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND, "帖子不存在");
        // 草稿只有作者本人和管理员可见（对齐博客「已发布才能被游客访问」的约束）
        boolean author = loginUser != null && loginUser.getId().equals(post.getAuthorId());
        ThrowUtils.throwIf(!isPublished(post.getStatus()) && !author && !isAdmin(loginUser),
                ErrorCode.NOT_FOUND, "帖子不存在或已下架");
        // 浏览数自增（博客用 Redis 计数 + 定时任务同步，这里直接数据库原子自增）
        if (isPublished(post.getStatus())) {
            this.mapper.incrementViewCount(post.getId());
            post.setViewCount(safeInt(post.getViewCount()) + 1);
        }
        return toDetailVO(post, loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityPostVO addPost(CommunityPostAddRequest addRequest, User loginUser) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录后再发布");
        ThrowUtils.throwIf(StrUtil.isBlank(addRequest.getTitle()), ErrorCode.PARAM_ERROR, "标题不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(addRequest.getContent()), ErrorCode.PARAM_ERROR, "正文不能为空");
        // 本项目 classpath 未引入 hibernate-validator，@Valid 不会生效，这里做等价的人工校验
        ThrowUtils.throwIf(addRequest.getTitle().trim().length() > 200, ErrorCode.PARAM_ERROR, "标题最多 200 字");
        ThrowUtils.throwIf(addRequest.getContent().length() > 20000, ErrorCode.PARAM_ERROR, "正文最多 20000 字");
        ThrowUtils.throwIf(addRequest.getExcerpt() != null && addRequest.getExcerpt().length() > 500,
                ErrorCode.PARAM_ERROR, "摘要最多 500 字");

        App app = resolveApp(addRequest.getAppId(), loginUser);
        LocalDateTime now = LocalDateTime.now();
        CommunityPost post = CommunityPost.builder()
                .title(addRequest.getTitle().trim())
                .content(addRequest.getContent())
                .excerpt(resolveExcerpt(addRequest.getExcerpt(), addRequest.getContent()))
                .cover(resolveCover(addRequest.getCover()))
                .appId(app == null ? null : app.getId())
                .authorId(loginUser.getId())
                .authorName(resolveNickname(loginUser))
                .authorAvatar(loginUser.getUserAvatar())
                .status(normalizeStatus(addRequest.getStatus()))
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        this.save(post);
        // 标签：不存在自动创建，重复绑定自动忽略
        communityTagService.bindTags(post.getId(), addRequest.getTags());
        return toDetailVO(post, loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityPostVO updatePost(CommunityPostUpdateRequest updateRequest, User loginUser) {
        ThrowUtils.throwIf(updateRequest == null, ErrorCode.PARAM_ERROR);
        CommunityPost post = this.getById(updateRequest.getId());
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND, "帖子不存在");
        assertAuthorOrAdmin(post, loginUser, "无权限修改该帖子");
        // 未引入 hibernate-validator，@Valid 不生效，长度在这里兜底
        ThrowUtils.throwIf(updateRequest.getTitle() != null && updateRequest.getTitle().trim().length() > 200,
                ErrorCode.PARAM_ERROR, "标题最多 200 字");
        ThrowUtils.throwIf(updateRequest.getContent() != null && updateRequest.getContent().length() > 20000,
                ErrorCode.PARAM_ERROR, "正文最多 20000 字");
        ThrowUtils.throwIf(updateRequest.getExcerpt() != null && updateRequest.getExcerpt().length() > 500,
                ErrorCode.PARAM_ERROR, "摘要最多 500 字");

        if (StrUtil.isNotBlank(updateRequest.getTitle())) {
            post.setTitle(updateRequest.getTitle().trim());
        }
        if (StrUtil.isNotBlank(updateRequest.getContent())) {
            post.setContent(updateRequest.getContent());
            // 正文变了但没传摘要时，重新生成摘要
            if (StrUtil.isBlank(updateRequest.getExcerpt())) {
                post.setExcerpt(resolveExcerpt(null, updateRequest.getContent()));
            }
        }
        if (updateRequest.getExcerpt() != null) {
            post.setExcerpt(StrUtil.sub(updateRequest.getExcerpt().trim(), 0, 500));
        }
        // 封面：传空字符串表示清除封面（前端用素材库/上传得到 url，也可手动粘贴）
        if (updateRequest.getCover() != null) {
            post.setCover(StrUtil.sub(updateRequest.getCover().trim(), 0, 500));
        }
        if (updateRequest.getStatus() != null) {
            post.setStatus(normalizeStatus(updateRequest.getStatus()));
        }
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);

        // 关联应用：传 0 表示解除关联（updateById 会忽略 null 字段，所以单独写 SQL 置空）
        if (updateRequest.getAppId() != null) {
            if (updateRequest.getAppId() == 0L) {
                this.mapper.clearAppId(post.getId());
                post.setAppId(null);
            } else {
                App app = resolveApp(updateRequest.getAppId(), loginUser);
                post.setAppId(app.getId());
                this.mapper.updateAppId(post.getId(), app.getId());
            }
        }
        // 标签：显式传了才覆盖（空数组表示清空）
        if (updateRequest.getTags() != null) {
            communityPostTagMapper.deleteByPostId(post.getId());
            communityTagService.bindTags(post.getId(), updateRequest.getTags());
        }
        return toDetailVO(post, loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAM_ERROR, "帖子 ID 无效");
        CommunityPost post = this.getById(id);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND, "帖子不存在");
        assertAuthorOrAdmin(post, loginUser, "无权限删除该帖子");

        // 级联清理（对齐博客 delete：清标签关联 + 物理删评论，这里额外清点赞）
        communityPostTagMapper.deleteByPostId(id);
        communityCommentMapper.physicalDeleteByPostId(id);
        communityPostLikeMapper.deleteByPostId(id);
        this.mapper.physicalDeleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityLikeResultVO like(Long postId, User loginUser) {
        CommunityPost post = requirePublishedPost(postId);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录后再点赞");
        int likeCount = safeInt(post.getLikeCount());
        if (hasLiked(postId, loginUser.getId())) {
            return new CommunityLikeResultVO(true, likeCount, "您已点赞");
        }
        try {
            communityPostLikeMapper.insert(CommunityPostLike.builder()
                    .postId(postId)
                    .userId(loginUser.getId())
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException e) {
            // 并发重复点赞：唯一索引兜底，按「已点赞」返回
            return new CommunityLikeResultVO(true, likeCount, "您已点赞");
        }
        this.mapper.updateLikeCount(postId, 1);
        return new CommunityLikeResultVO(true, likeCount + 1, "点赞成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommunityLikeResultVO unlike(Long postId, User loginUser) {
        CommunityPost post = requirePublishedPost(postId);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        int likeCount = safeInt(post.getLikeCount());
        int deleted = communityPostLikeMapper.deleteByQuery(QueryWrapper.create()
                .eq("postId", postId)
                .eq("userId", loginUser.getId()));
        if (deleted > 0) {
            this.mapper.updateLikeCount(postId, -1);
            return new CommunityLikeResultVO(false, Math.max(likeCount - 1, 0), "已取消点赞");
        }
        return new CommunityLikeResultVO(false, likeCount, "您尚未点赞");
    }

    @Override
    public boolean hasLiked(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }
        long count = communityPostLikeMapper.selectCountByQuery(QueryWrapper.create()
                .eq("postId", postId)
                .eq("userId", userId));
        return count > 0;
    }

    @Override
    public CommunityStatsVO getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long postTotal = this.mapper.countPublished();
        long todayTotal = this.mapper.countPublishedAfter(todayStart);
        long commentTotal = communityCommentMapper.countAll();
        long userTotal = userService.count();
        return new CommunityStatsVO(postTotal, todayTotal, commentTotal, userTotal);
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 构建列表查询条件（对齐博客 loadPublished：已发布 + 标签 + 时间倒序）
     */
    private QueryWrapper buildListWrapper(CommunityPostQueryRequest query, User loginUser) {
        QueryWrapper wrapper = QueryWrapper.create();
        boolean onlyMine = Boolean.TRUE.equals(query.getOnlyMine());
        if (onlyMine) {
            ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
            // 我的帖子：包含草稿；未显式指定状态时不限制
            wrapper.eq("authorId", loginUser.getId());
            if (query.getStatus() != null) {
                wrapper.eq("status", query.getStatus());
            }
        } else {
            // 公开列表：只展示已发布，且不允许通过参数越权查看草稿
            wrapper.eq("status", STATUS_PUBLISHED);
        }
        if (query.getId() != null) {
            wrapper.eq("id", query.getId());
        }
        if (query.getAuthorId() != null) {
            wrapper.eq("authorId", query.getAuthorId());
        }
        if (query.getAppId() != null) {
            wrapper.eq("appId", query.getAppId());
        }
        // 关键词：标题或正文模糊匹配（分组 OR，避免污染其他 AND 条件）
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String likeArg = "%" + query.getKeyword().trim() + "%";
            wrapper.and(new QueryColumn("title").like(likeArg).or(new QueryColumn("content").like(likeArg)));
        }
        // 标签过滤：先按标签名反查帖子 ID（博客是查全量再内存过滤，这里下推到 SQL）
        if (StrUtil.isNotBlank(query.getTag())) {
            List<Long> postIds = communityTagService.findPublishedPostIdsByTagName(query.getTag());
            if (CollUtil.isEmpty(postIds)) {
                wrapper.eq("id", -1L);
            } else {
                wrapper.in("id", postIds);
            }
        }
        // 排序：白名单字段 + 创建时间兜底，保证分页稳定（sortField 为空时不能直接 contains，Set.of 不允许 null）
        String requestedField = query.getSortField();
        String sortField = (requestedField != null && SORTABLE_FIELDS.contains(requestedField))
                ? requestedField : "createTime";
        boolean ascend = "ascend".equalsIgnoreCase(query.getSortOrder());
        wrapper.orderBy(sortField, ascend);
        if (!"createTime".equals(sortField)) {
            wrapper.orderBy("createTime", false);
        }
        return wrapper;
    }

    /**
     * 校验并返回关联应用（appId 为空返回 null）
     */
    private App resolveApp(Long appId, User loginUser) {
        if (appId == null || appId <= 0) {
            return null;
        }
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND, "关联的应用不存在");
        boolean owner = loginUser != null && loginUser.getId().equals(app.getUserId());
        ThrowUtils.throwIf(!owner && !isAdmin(loginUser), ErrorCode.NOT_AUTH_ERROR, "只能关联自己创建的应用");
        return app;
    }

    /**
     * 校验是作者本人或管理员
     */
    private void assertAuthorOrAdmin(CommunityPost post, User loginUser, String message) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        boolean owner = loginUser.getId().equals(post.getAuthorId());
        ThrowUtils.throwIf(!owner && !isAdmin(loginUser), ErrorCode.NOT_AUTH_ERROR, message);
    }

    /**
     * 查询已发布帖子，不存在/未发布直接抛出
     */
    private CommunityPost requirePublishedPost(Long postId) {
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAM_ERROR, "帖子 ID 无效");
        CommunityPost post = this.getById(postId);
        ThrowUtils.throwIf(post == null || !isPublished(post.getStatus()),
                ErrorCode.NOT_FOUND, "帖子不存在或已下架");
        return post;
    }

    private boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }

    private boolean isPublished(Integer status) {
        return status != null && status == STATUS_PUBLISHED;
    }

    /**
     * 状态归一化：只允许 0 草稿 / 1 发布，默认发布
     */
    private int normalizeStatus(Integer status) {
        if (status == null) {
            return STATUS_PUBLISHED;
        }
        ThrowUtils.throwIf(status != STATUS_DRAFT && status != STATUS_PUBLISHED,
                ErrorCode.PARAM_ERROR, "帖子状态不合法");
        return status;
    }

    /**
     * 摘要：优先用传入值，否则去掉 Markdown/HTML 标记后截取前 150 字
     */
    private String resolveExcerpt(String excerpt, String content) {
        if (StrUtil.isNotBlank(excerpt)) {
            return StrUtil.sub(excerpt.trim(), 0, 500);
        }
        String plain = StrUtil.blankToDefault(content, "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[#>*`~\\[\\]()!-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StrUtil.sub(plain, 0, EXCERPT_LENGTH);
    }

    /**
     * 封面：传了合法地址就用，否则用 picsum 按随机 seed 生成（与前端默认封面同源）
     */
    private String resolveCover(String cover) {
        if (StrUtil.isNotBlank(cover) && cover.trim().matches("^(https?://|//|/).*")) {
            return StrUtil.sub(cover.trim(), 0, 500);
        }
        return "https://picsum.photos/seed/community-" + RandomUtil.randomString(8) + "/800/600";
    }

    /**
     * 昵称兜底：昵称为空用账号
     */
    private String resolveNickname(User user) {
        String nickname = StrUtil.blankToDefault(user.getUserName(), user.getUserAccount());
        return StrUtil.sub(nickname, 0, 50);
    }

    /**
     * 批量查询列表页涉及的应用，避免 N+1
     */
    private Map<Long, App> loadApps(List<CommunityPost> posts) {
        List<Long> appIds = posts.stream()
                .map(CommunityPost::getAppId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, App> appMap = new HashMap<>();
        if (appIds.isEmpty()) {
            return appMap;
        }
        List<App> apps = appService.listByIds(appIds);
        if (apps != null) {
            appMap = apps.stream().collect(Collectors.toMap(App::getId, a -> a, (a, b) -> a));
        }
        return appMap;
    }

    /**
     * 批量查询当前用户在本页帖子中的点赞状态，避免逐条查询
     */
    private Set<Long> loadLikedPostIds(List<CommunityPost> posts, User loginUser) {
        if (loginUser == null || CollUtil.isEmpty(posts)) {
            return Set.of();
        }
        List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
        List<CommunityPostLike> likes = communityPostLikeMapper.selectListByQuery(QueryWrapper.create()
                .eq("userId", loginUser.getId())
                .in("postId", postIds));
        if (CollUtil.isEmpty(likes)) {
            return Set.of();
        }
        Set<Long> liked = new HashSet<>();
        for (CommunityPostLike like : likes) {
            liked.add(like.getPostId());
        }
        return liked;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 列表项 VO
     */
    private CommunityPostListVO toListVO(CommunityPost post, Map<Long, App> appMap,
                                         Set<Long> likedPostIds, User loginUser) {
        CommunityPostListVO vo = new CommunityPostListVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setExcerpt(post.getExcerpt());
        vo.setCover(post.getCover());
        vo.setAppId(post.getAppId());
        App app = post.getAppId() == null ? null : appMap.get(post.getAppId());
        vo.setAppName(app == null ? null : app.getAppName());
        vo.setAuthorId(post.getAuthorId());
        vo.setAuthorName(post.getAuthorName());
        vo.setAuthorAvatar(post.getAuthorAvatar());
        vo.setStatus(post.getStatus());
        vo.setViewCount(safeInt(post.getViewCount()));
        vo.setLikeCount(safeInt(post.getLikeCount()));
        vo.setCommentCount(safeInt(post.getCommentCount()));
        vo.setTags(communityTagService.listNamesByPostId(post.getId()));
        vo.setLiked(likedPostIds.contains(post.getId()));
        vo.setMine(loginUser != null && loginUser.getId().equals(post.getAuthorId()));
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }

    /**
     * 详情 VO（对应博客 toDetailVO，额外补关联应用信息与当前用户点赞状态）
     */
    private CommunityPostVO toDetailVO(CommunityPost post, User loginUser) {
        CommunityPostVO vo = new CommunityPostVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setExcerpt(post.getExcerpt());
        vo.setCover(post.getCover());
        vo.setAppId(post.getAppId());
        if (post.getAppId() != null) {
            App app = appService.getById(post.getAppId());
            if (app != null) {
                vo.setAppName(app.getAppName());
                vo.setAppCover(app.getCover());
                vo.setAppCodeGenType(app.getCodeGenType());
                vo.setAppDeployKey(app.getDeployKey());
            }
        }
        vo.setAuthorId(post.getAuthorId());
        vo.setAuthorName(post.getAuthorName());
        vo.setAuthorAvatar(post.getAuthorAvatar());
        vo.setStatus(post.getStatus());
        vo.setViewCount(safeInt(post.getViewCount()));
        vo.setLikeCount(safeInt(post.getLikeCount()));
        vo.setCommentCount(safeInt(post.getCommentCount()));
        vo.setTags(communityTagService.listNamesByPostId(post.getId()));
        vo.setLiked(loginUser != null && hasLiked(post.getId(), loginUser.getId()));
        vo.setMine(loginUser != null && loginUser.getId().equals(post.getAuthorId()));
        vo.setCreateTime(post.getCreateTime());
        vo.setUpdateTime(post.getUpdateTime());
        return vo;
    }
}
