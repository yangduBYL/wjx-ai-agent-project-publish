package com.wjx.wjxaiagentprojectback.damon.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 社区点赞结果 VO（对应个人博客系统的 LikeResultVO）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityLikeResultVO implements Serializable {

    /**
     * 操作后是否处于已点赞状态
     */
    private Boolean liked;

    /**
     * 操作后的点赞总数
     */
    private Integer likeCount;

    /**
     * 提示信息
     */
    private String message;
}
