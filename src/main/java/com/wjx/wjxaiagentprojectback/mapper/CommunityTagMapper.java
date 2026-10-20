package com.wjx.wjxaiagentprojectback.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityTag;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityTagVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 社区标签 映射层。
 *
 * <p>改写自个人博客系统 TagMapper 的 findNamesByArticleId（关联查询标签名），
 * 额外提供「标签 + 帖子数」聚合与「按标签名反查帖子 ID」两个查询。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityTagMapper extends BaseMapper<CommunityTag> {

    /**
     * 查询某个帖子绑定的标签名（按标签 ID 升序，保证展示稳定）
     */
    @Select("SELECT t.name FROM community_tag t " +
            "JOIN community_post_tag pt ON pt.tagId = t.id " +
            "WHERE pt.postId = #{postId} ORDER BY t.id ASC")
    List<String> findNamesByPostId(@Param("postId") Long postId);

    /**
     * 标签列表 + 已发布帖子数（社区筛选栏用，按热度倒序）
     */
    @Select("SELECT t.id AS id, t.name AS name, COUNT(p.id) AS postCount " +
            "FROM community_tag t " +
            "JOIN community_post_tag pt ON pt.tagId = t.id " +
            "JOIN community_post p ON p.id = pt.postId AND p.status = 1 AND p.isDelete = 0 " +
            "GROUP BY t.id, t.name ORDER BY postCount DESC, t.id ASC")
    List<CommunityTagVO> listWithPostCount();

    /**
     * 按标签名反查已发布帖子 ID（对应博客 ArticleService#loadPublished 的标签过滤思路，
     * 但把「查全部再在内存过滤」改为一次关联查询，避免全表加载）
     */
    @Select("SELECT pt.postId FROM community_post_tag pt " +
            "JOIN community_tag t ON t.id = pt.tagId " +
            "JOIN community_post p ON p.id = pt.postId AND p.status = 1 AND p.isDelete = 0 " +
            "WHERE t.name = #{name}")
    List<Long> findPublishedPostIdsByTagName(@Param("name") String name);
}
