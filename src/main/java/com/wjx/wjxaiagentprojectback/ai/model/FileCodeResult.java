package com.wjx.wjxaiagentprojectback.ai.model;

import jdk.jfr.Description;
import lombok.Data;
@Description("生成多段代码的结果")
@Data
public class FileCodeResult {
    /**
     * html代码
     */
    @Description("html代码")
    private String htmlCode;
    /**
     * css代码
     */
    @Description("css代码")
    private String cssCode;
    /**
     * js代码
     */
    @Description("js代码")
    private String jsCode;
    /**
     * 描述
     */
    @Description("生成代码的描述")
    private String description;
}
