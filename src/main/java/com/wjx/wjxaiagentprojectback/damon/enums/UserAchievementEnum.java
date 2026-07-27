package com.wjx.wjxaiagentprojectback.damon.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum UserAchievementEnum {
    // 成就枚举
    FIRST_LOGIN("首次登录", "FIRST_LOGIN"),
    FiRST_CREATE("首次创作", "first_create"),
    FiRST_COMMENT("首次评论", "first_comment"),
    FiRST_SHARE("首次分享", "first_share"),
    //五次制作成就
    FIVE_CREATE("五次制作", "five_create"),
    //十次制作成就
    TEN_CREATE("十次制作", "ten_create"),
    //十次以上，制作大师成就
    TEN_CREATE_MASTER("制作大师", "ten_create_master");

    private final String text;
    private final String value;

    /**
     * 枚举的构造方法
     *
     * @param text
     * @param value
     */
    UserAchievementEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
    public static UserAchievementEnum getByValueEnum(String value){
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        for(UserAchievementEnum AchievementEnum : UserAchievementEnum.values()){
            if(AchievementEnum.value.equals(value))
                return AchievementEnum;
        }
        return null;
    }
}
