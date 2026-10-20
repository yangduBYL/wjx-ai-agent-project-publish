package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 社区标签 VO（对应个人博客系统 TagController 返回的标签名 + 帖子数）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityTagVO implements Serializable {

    /**
     * 标签 ID
     */
    private Long id;

    /**
     * 标签名
     */
    private String name;

    /**
     * 使用该标签的已发布帖子数
     */
    private Long postCount;
}
