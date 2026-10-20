package com.wjx.wjxaiagentprojectback.damon.dto.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 发布社区帖子 请求体。
 *
 * <p>改写自个人博客系统的 ArticleRequest（title/content/excerpt/cover/tags），
 * 去掉 slug / status 由服务端兜底，新增 appId 用于关联已生成的 AI 应用。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
public class CommunityPostAddRequest implements Serializable {

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多 200 字")
    private String title;

    /**
     * 正文
     */
    @NotBlank(message = "正文不能为空")
    @Size(max = 20000, message = "正文最多 20000 字")
    private String content;

    /**
     * 摘要（可选，为空时自动截取正文前 150 字）
     */
    @Size(max = 500, message = "摘要最多 500 字")
    private String excerpt;

    /**
     * 封面图地址（可选，为空时自动生成占位封面）
     */
    @Size(max = 500, message = "封面地址过长")
    private String cover;

    /**
     * 关联的 AI 生成应用 ID（可选，分享作品时填写）
     */
    private Long appId;

    /**
     * 标签列表（最多 5 个）
     */
    @Size(max = 5, message = "最多添加 5 个标签")
    private List<String> tags;

    /**
     * 状态：0 草稿 1 发布（默认发布）
     */
    private Integer status;
}
