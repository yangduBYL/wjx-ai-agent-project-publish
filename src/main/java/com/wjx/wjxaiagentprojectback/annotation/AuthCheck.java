package com.wjx.wjxaiagentprojectback.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，权限校验注解
 */
@Target(ElementType.METHOD)//注解在方法上
@Retention(RetentionPolicy.RUNTIME)//注解在运行时有效
public @interface AuthCheck {
    /**
     * 权限校验，必填
     * @return
     * 自定义注解中，属性后面加括号 () 是 Java 语言的强制语法规定，这与普通类中的属性完全不同。
     */
    String mustRole() default "";
}
