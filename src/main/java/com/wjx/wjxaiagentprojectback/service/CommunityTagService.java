package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.service.IService;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityTag;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityTagVO;

import java.util.List;

/**
 * 社区标签 服务层（对应个人博客系统的 TagService 职责）。
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface CommunityTagService extends IService<CommunityTag> {

    /**
     * 标签列表 + 已发布帖子数（按热度倒序）
     */
    List<CommunityTagVO> listWithPostCount();

    /**
     * 查询帖子绑定的标签名
     */
    List<String> listNamesByPostId(Long postId);

    /**
     * 绑定标签（标签不存在时自动创建，重复绑定自动忽略）
     *
     * @param postId   帖子 ID
     * @param tagNames 标签名列表
     */
    void bindTags(Long postId, List<String> tagNames);

    /**
     * 按标签名反查已发布帖子 ID
     */
    List<Long> findPublishedPostIdsByTagName(String name);
}
