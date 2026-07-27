package com.wjx.wjxaiagentprojectback.damon.dto.user;


import com.wjx.wjxaiagentprojectback.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**包含父类属性：当设置 callSuper = true 时，生成的 equals() 方法会调用父类的 equals() 方法，hashCode() 方法也会调用父类的 hashCode() 方法。这意味着只有当当前类的属性相等且父类的属性也相等时，两个对象才会被判定为相等1
 *
 * 使用场景：当一个类继承自非 Object 的父类，且需要保证对象的相等性依赖于整个继承链上的所有属性时，必须设置 callSuper = true1。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    private Long id;
    private String userName;
    private String userAccount;
    private String userRole;
    private String userProfile;
}
