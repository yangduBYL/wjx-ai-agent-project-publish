package com.wjx.wjxaiagentprojectback.damon.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 社区标签 实体类。
 *
 * <p>改写自个人博客系统的 tag 表，主键由自增改为雪花 ID。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("community_tag")
public class CommunityTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签 ID
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 标签名
     */
    private String name;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;
}
