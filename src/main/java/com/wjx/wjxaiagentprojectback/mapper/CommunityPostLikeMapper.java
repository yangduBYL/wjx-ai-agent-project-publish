package com.wjx.wjxaiagentprojectback.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPostLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 社区帖子点赞 映射层。
 *
 * <p>改写自个人博客系统用 Redis Set 做的点赞去重，这里落到 MySQL 唯一索引。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityPostLikeMapper extends BaseMapper<CommunityPostLike> {

    /**
     * 物理删除某帖子的全部点赞记录（删帖时调用）
     */
    @Delete("DELETE FROM community_post_like WHERE postId = #{postId}")
    int deleteByPostId(@Param("postId") Long postId);
}
