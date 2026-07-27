package com.wjx.wjxaiagentprojectback.damon.dto.chathistory;

import com.wjx.wjxaiagentprojectback.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 如果不加 callSuper = true，比较两个 ChatHistoryQueryRequest 对象时，会忽略分页参数。即使一个请求查第1页，另一个查第2页，只要 id、message 等子类属性相同，就会被误判为同一个查询对象
 */
@EqualsAndHashCode(callSuper = true)
//在生成 equals() 和 hashCode() 方法时，将父类的属性也纳入计算范围。
@Data
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型（user/ai）
     */
    private String messageType;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 游标查询 - 最后一条记录的创建时间
     * 用于分页查询，获取早于此时间的记录
     */
    private LocalDateTime lastCreateTime;



    private static final long serialVersionUID = 1L;
}
