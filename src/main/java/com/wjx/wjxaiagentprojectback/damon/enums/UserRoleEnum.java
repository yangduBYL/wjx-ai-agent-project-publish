package com.wjx.wjxaiagentprojectback.damon.enums;

import cn.hutool.core.util.ObjUtil;

import lombok.Getter;

/**
 * 作用：权限管理和属性补充
 */
@Getter
public enum UserRoleEnum {
    /**
     * 定义枚举的值
     */
    USER("用户", "user"),
    ADMIN("管理员", "admin");;
    private final String text;
    private final String value;

    /**
     * 枚举的构造方法
     *
     * @param text
     * @param value
     */
    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据枚举的值反查枚举对象
     * @param value
     * @return
     */
    public static UserRoleEnum getEnumByValue(String value) {
        //判断是否为空
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        //遍历枚举
        for (UserRoleEnum e : UserRoleEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
