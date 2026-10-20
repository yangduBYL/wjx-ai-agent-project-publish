package com.wjx.wjxaiagentprojectback.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPost;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 社区帖子 映射层。
 *
 * <p>改写自个人博客系统 ArticleMapper：保留「物理删除绕过逻辑删除」的做法，
 * 并把博客里放在 Redis 的浏览数自增、点赞数刷新改为数据库原子自增。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {

    /**
     * 浏览数原子自增（避免并发下读-改-写丢失更新）
     */
    @Update("UPDATE community_post SET viewCount = viewCount + 1 WHERE id = #{id} AND isDelete = 0")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 点赞数增减（delta 为 +1 / -1，兜底不小于 0）
     */
    @Update("UPDATE community_post SET likeCount = GREATEST(likeCount + #{delta}, 0) WHERE id = #{id}")
    int updateLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 评论数增减（delta 为 +1 / -1，兜底不小于 0）
     */
    @Update("UPDATE community_post SET commentCount = GREATEST(commentCount + #{delta}, 0) WHERE id = #{id}")
    int updateCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 物理删除帖子（绕过逻辑删除，用于作者/管理员彻底删除）
     */
    @Delete("DELETE FROM community_post WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 清空帖子关联的应用（updateById 会忽略 null 字段，故单独写 SQL）
     */
    @Update("UPDATE community_post SET appId = NULL WHERE id = #{id}")
    int clearAppId(@Param("id") Long id);

    /**
     * 更新帖子关联的应用
     */
    @Update("UPDATE community_post SET appId = #{appId} WHERE id = #{id}")
    int updateAppId(@Param("id") Long id, @Param("appId") Long appId);

    /**
     * 统计已发布帖子数
     */
    @Select("SELECT COUNT(*) FROM community_post WHERE status = 1 AND isDelete = 0")
    long countPublished();

    /**
     * 统计某时间之后发布的帖子数（用于「今日新增」）
     */
    @Select("SELECT COUNT(*) FROM community_post WHERE status = 1 AND isDelete = 0 AND createTime >= #{start}")
    long countPublishedAfter(@Param("start") LocalDateTime start);

    /**
     * 统计把某个图片地址当作封面的帖子数（素材删除前的引用校验）
     */
    @Select("SELECT COUNT(*) FROM community_post WHERE cover = #{cover} AND isDelete = 0")
    long countByCover(@Param("cover") String cover);
}
