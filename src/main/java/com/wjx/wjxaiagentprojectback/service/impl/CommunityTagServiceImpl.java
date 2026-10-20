package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjx.wjxaiagentprojectback.damon.entity.CommunityTag;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityTagVO;
import com.wjx.wjxaiagentprojectback.mapper.CommunityPostTagMapper;
import com.wjx.wjxaiagentprojectback.mapper.CommunityTagMapper;
import com.wjx.wjxaiagentprojectback.service.CommunityTagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 社区标签 服务层实现。
 *
 * <p>改写自个人博客系统 ArticleService#saveTags：同样是「查不到就新建标签，
 * 然后插入 article_tag 关联」，这里把标签名做了去重/去空/长度限制，
 * 并用 INSERT IGNORE 处理重复绑定。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Slf4j
@Service
public class CommunityTagServiceImpl extends ServiceImpl<CommunityTagMapper, CommunityTag>
        implements CommunityTagService {

    /**
     * 单个帖子最多绑定的标签数（与 DTO 校验保持一致）
     */
    private static final int MAX_TAGS_PER_POST = 5;

    /**
     * 单个标签名最大长度（与表结构一致）
     */
    private static final int MAX_TAG_NAME_LENGTH = 20;

    @Resource
    private CommunityPostTagMapper communityPostTagMapper;

    @Override
    public List<CommunityTagVO> listWithPostCount() {
        return this.mapper.listWithPostCount();
    }

    @Override
    public List<String> listNamesByPostId(Long postId) {
        if (postId == null) {
            return List.of();
        }
        return this.mapper.findNamesByPostId(postId);
    }

    @Override
    public void bindTags(Long postId, List<String> tagNames) {
        if (postId == null || CollUtil.isEmpty(tagNames)) {
            return;
        }
        // 1. 规范化：去空白、去重、截断超长标签、限制数量
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawName : tagNames) {
            if (StrUtil.isBlank(rawName)) {
                continue;
            }
            String name = StrUtil.sub(rawName.trim(), 0, MAX_TAG_NAME_LENGTH);
            if (StrUtil.isBlank(name)) {
                continue;
            }
            normalized.add(name);
            if (normalized.size() >= MAX_TAGS_PER_POST) {
                break;
            }
        }
        // 2. 逐个「查不到就创建」，再绑定关联
        for (String name : normalized) {
            CommunityTag tag = this.getOne(QueryWrapper.create().eq("name", name));
            if (tag == null) {
                CommunityTag newTag = CommunityTag.builder()
                        .name(name)
                        .createTime(LocalDateTime.now())
                        .build();
                try {
                    this.save(newTag);
                    tag = newTag;
                } catch (DuplicateKeyException e) {
                    // 并发下另一个请求先创建了同名标签，回查即可
                    tag = this.getOne(QueryWrapper.create().eq("name", name));
                }
            }
            if (tag != null && tag.getId() != null) {
                communityPostTagMapper.insertIgnore(postId, tag.getId());
            }
        }
    }

    @Override
    public List<Long> findPublishedPostIdsByTagName(String name) {
        if (StrUtil.isBlank(name)) {
            return List.of();
        }
        return this.mapper.findPublishedPostIdsByTagName(name.trim());
    }
}
