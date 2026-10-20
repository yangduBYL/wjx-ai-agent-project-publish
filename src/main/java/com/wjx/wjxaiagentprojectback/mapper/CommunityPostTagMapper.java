package com.wjx.wjxaiagentprojectback.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityPostTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 帖子-标签关联 映射层。
 *
 * <p>改写自个人博客系统 ArticleTagMapper：联合主键，写入/删除走显式 SQL。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityPostTagMapper extends BaseMapper<CommunityPostTag> {

    /**
     * 插入关联（IGNORE 避免重复绑定同一标签时报唯一键冲突）
     */
    @Insert("INSERT IGNORE INTO community_post_tag (postId, tagId) VALUES (#{postId}, #{tagId})")
    int insertIgnore(@Param("postId") Long postId, @Param("tagId") Long tagId);

    /**
     * 删除帖子下的全部标签关联
     */
    @Delete("DELETE FROM community_post_tag WHERE postId = #{postId}")
    int deleteByPostId(@Param("postId") Long postId);
}
