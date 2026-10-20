package com.wjx.wjxaiagentprojectback.controller;

import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.damon.vo.CommunityTagVO;
import com.wjx.wjxaiagentprojectback.service.CommunityTagService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区标签 控制层（改写自个人博客系统 TagController）。
 *
 * @author wjx
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/community/tag")
public class CommunityTagController {

    @Resource
    private CommunityTagService communityTagService;

    /**
     * 标签列表（附带已发布帖子数，按热度倒序）
     */
    @GetMapping("/list")
    public BaseResponse<List<CommunityTagVO>> list() {
        return ResultUtils.success(communityTagService.listWithPostCount());
    }
}
