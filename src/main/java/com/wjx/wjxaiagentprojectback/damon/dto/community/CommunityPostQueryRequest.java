package com.wjx.wjxaiagentprojectback.damon.dto.community;

import com.wjx.wjxaiagentprojectback.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区帖子分页查询 请求体。
 *
 * <p>参考个人博客系统 ArticleController#list（page/size/tag）扩展：
 * 增加关键词搜索、作者筛选、应用筛选、排序字段（createTime/likeCount/viewCount）。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostQueryRequest extends PageRequest {

    /**
     * 帖子 ID
     */
    private Long id;

    /**
     * 关键词（标题 / 正文模糊匹配）
     */
    private String keyword;

    /**
     * 标签名精确匹配
     */
    private String tag;

    /**
     * 作者用户 ID
     */
    private Long authorId;

    /**
     * 关联应用 ID
     */
    private Long appId;

    /**
     * 状态：0 草稿 1 已发布（普通列表固定查 1）
     */
    private Integer status;

    /**
     * 是否只查当前登录用户的帖子（配合 authorId 由服务端填充）
     */
    private Boolean onlyMine;
}
