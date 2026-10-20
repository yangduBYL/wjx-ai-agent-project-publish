package com.wjx.wjxaiagentprojectback.damon.dto.community;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新社区帖子 请求体。
 *
 * <p>改写自个人博客系统的 ArticleRequest + 更新接口（按 id 更新）。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class CommunityPostUpdateRequest implements Serializable {

    /**
     * 帖子 ID
     */
    @NotNull(message = "帖子 ID 不能为空")
    private Long id;

    /**
     * 标题
     */
    @Size(max = 200, message = "标题最多 200 字")
    private String title;

    /**
     * 正文
     */
    @Size(max = 20000, message = "正文最多 20000 字")
    private String content;

    /**
     * 摘要
     */
    @Size(max = 500, message = "摘要最多 500 字")
    private String excerpt;

    /**
     * 封面
     */
    @Size(max = 500, message = "封面地址过长")
    private String cover;

    /**
     * 关联应用 ID（传 0 表示解除关联）
     */
    private Long appId;

    /**
     * 标签列表（传空数组表示清空标签）
     */
    @Size(max = 5, message = "最多添加 5 个标签")
    private List<String> tags;

    /**
     * 状态：0 草稿 1 发布
     */
    private Integer status;
}
