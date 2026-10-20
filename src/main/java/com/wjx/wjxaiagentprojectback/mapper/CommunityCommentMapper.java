package com.wjx.wjxaiagentprojectback.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 社区评论 映射层。
 *
 * <p>改写自个人博客系统 CommentMapper：保留 physicalDeleteByArticleId 式的
 * 「删帖时物理清理评论」，新增评论总数统计。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityCommentMapper extends BaseMapper<CommunityComment> {

    /**
     * 物理删除某帖子下的所有评论（删帖时调用，避免逻辑删除数据堆积）
     */
    @Delete("DELETE FROM community_comment WHERE postId = #{postId}")
    int physicalDeleteByPostId(@Param("postId") Long postId);

    /**
     * 物理删除某条评论及其直接子回复
     */
    @Delete("DELETE FROM community_comment WHERE id = #{id} OR parentId = #{id}")
    int physicalDeleteWithReplies(@Param("id") Long id);

    /**
     * 统计评论总数（已逻辑删除的不计）
     */
    @Select("SELECT COUNT(*) FROM community_comment WHERE isDelete = 0")
    long countAll();
}
