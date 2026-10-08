package com.wjx.wjxaiagentprojectback.damon.enums;

import lombok.Getter;
import opennlp.tools.util.StringUtil;

@Getter
public enum CodeTypeEnum {
    HTML("原生HTML模式","html"),
    MULTI_FILE("原生多文件模式","multi_file"),
    VUE_PROJECT("Vue 工程模式", "vue_project");


    private String text;
    private String code;
    CodeTypeEnum(String text,String code){
        this.text=text;
        this.code=code;
    }


    /**
     * 通过value来获取对应的key,枚举
     */
    public static CodeTypeEnum getValueCode(String code){
        if(StringUtil.isEmpty(code)){
            return null;
        }
        //在进行循环匹配
        for(CodeTypeEnum codeTypeEnum:CodeTypeEnum.values()){
            if(codeTypeEnum.getCode().equals(code)){
                return codeTypeEnum;
            }
        }
        return null;
    }

}
