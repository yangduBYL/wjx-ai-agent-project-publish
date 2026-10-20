package com.wjx.wjxaiagentprojectback.damon.dto.material;

import com.wjx.wjxaiagentprojectback.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 素材分页查询 请求体。
 *
 * <p>参考个人博客系统 media 表只做全量列表，这里按用户隔离并支持文件名搜索。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialQueryRequest extends PageRequest {

    /**
     * 文件名关键词（模糊匹配原始文件名）
     */
    private String keyword;

    /**
     * 上传用户 ID（仅管理员可指定，普通用户强制为本人）
     */
    private Long userId;
}
