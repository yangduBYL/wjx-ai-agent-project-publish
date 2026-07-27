package com.wjx.wjxaiagentprojectback.common;

import lombok.Data;

/**
 * 分页请求包装类
 */
@Data
public class PageRequest {
    /**
     * 当前页号
     */
    private int pageNum=1;
    /**
     * 每页大小
     */
    private int pageSize=10;
    /**
     * 排序字段
     */
    private String sortField;
    /**
     * 排序顺序（“默认降序”、“ascend” 升序）
     */
    private String sortOrder="descend";
}
