package com.wjx.wjxaiagentprojectback.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jdk.jfr.Description;
import lombok.Data;

/**
 * 规范结构化输出，html代码
 */
@Description("生成单个的html文件的结果")
@Data
public class HtmlCodeResult {
    /**
     * html代码
     */
    @Description("html代码")
    private String htmlCode;
    /**
     * 描述
     */
    @Description("生成代码的描述描述")
    private String description;
}
