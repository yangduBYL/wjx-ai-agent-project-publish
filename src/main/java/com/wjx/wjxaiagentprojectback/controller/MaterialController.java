package com.wjx.wjxaiagentprojectback.controller;

import com.mybatisflex.core.paginate.Page;
import com.wjx.wjxaiagentprojectback.annotation.RateLimit;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.DeleteRequest;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.damon.dto.material.MaterialQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.RateLimitType;
import com.wjx.wjxaiagentprojectback.damon.vo.MaterialVO;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.service.MaterialService;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材库 控制层。
 *
 * <p>改写自个人博客系统 AdminController#upload（POST /api/admin/upload 返回 { url }）：
 * 这里改为返回完整素材信息，并补齐「我的素材分页列表」「删除素材」两个接口，
 * 上传需要登录（素材按用户隔离），接口前缀 /material。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/material")
public class MaterialController {

    @Resource
    private MaterialService materialService;

    @Resource
    private UserService userService;

    /**
     * 上传图片素材（multipart/form-data，字段名 file）
     *
     * @param file 图片文件（JPG/PNG/GIF/WebP，≤10MB）
     * @return 素材信息，url 可直接用作社区帖子封面
     */
    @PostMapping("/upload")
    @RateLimit(key = "material_upload", rate = 20, rateInterval = 60,
            limitType = RateLimitType.USER, message = "上传过于频繁，请稍后再试")
    public BaseResponse<MaterialVO> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return ResultUtils.success(materialService.upload(file, requireLogin(request)));
    }

    /**
     * 我的素材分页列表
     */
    @PostMapping("/list/page/mine")
    public BaseResponse<Page<MaterialVO>> listMine(
            @RequestBody(required = false) MaterialQueryRequest queryRequest,
            HttpServletRequest request) {
        return ResultUtils.success(materialService.listMy(queryRequest, requireLogin(request)));
    }

    /**
     * 删除素材（上传者或管理员）
     */
    @DeleteMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAM_ERROR, "素材 ID 不能为空");
        materialService.delete(deleteRequest.getId(), requireLogin(request));
        return ResultUtils.success(true);
    }

    private User requireLogin(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
    }
}
