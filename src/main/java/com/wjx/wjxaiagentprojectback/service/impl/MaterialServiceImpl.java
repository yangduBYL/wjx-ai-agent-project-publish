package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;
import com.wjx.wjxaiagentprojectback.damon.dto.material.MaterialQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.Material;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.MaterialVO;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.manager.CosManager;
import com.wjx.wjxaiagentprojectback.mapper.CommunityPostMapper;
import com.wjx.wjxaiagentprojectback.mapper.MaterialMapper;
import com.wjx.wjxaiagentprojectback.service.MaterialService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 素材 服务层实现。
 *
 * <p>存储策略（对齐博客 UploadService 的「OSS 优先、本地兜底」思路，本项目换成腾讯云 COS）：
 * <ol>
 *   <li>优先上传到 COS（materials/{userId}/{yyyy/MM/dd}/{uuid}.{ext}），拿到公网永久地址</li>
 *   <li>COS 未配置/上传异常时，回退到本地 temp/material/{userId}/，地址为 /api/material/file/{userId}/{fileName}</li>
 * </ol>
 * 本地兜底目录由 MaterialResourceConfig 注册为静态资源路径，前端可直接 img src 访问。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
@Slf4j
@Service
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {

    /**
     * 单张图片最大 10MB（与 application.yml 的 multipart 限制保持一致）
     */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    /**
     * 允许的扩展名（与博客 UploadService 一致：仅常见位图，不放 svg 以免脚本注入）
     */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 允许的 MIME 类型
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "image/pjpeg", "image/x-png");

    /**
     * COS 对象键前缀
     */
    private static final String COS_KEY_PREFIX = "materials/";

    /**
     * 本地兜底访问地址前缀（与 context-path /api 对应）
     */
    private static final String LOCAL_URL_PREFIX = "/api/material/file/";

    /**
     * 单页最大条数
     */
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private CosManager cosManager;

    @Resource
    private CommunityPostMapper communityPostMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialVO upload(MultipartFile file, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录后再上传");
        validateFile(file);

        String extension = resolveExtension(file);
        String fileName = IdUtil.simpleUUID() + "." + extension;
        String cosKey = COS_KEY_PREFIX + loginUser.getId() + "/"
                + DateUtil.format(new Date(), "yyyy/MM/dd") + "/" + fileName;

        String url;
        String storagePath;
        // 先把上传流落到临时文件：COS SDK 只接受 File，且本地兜底也需要文件副本
        File tempFile = null;
        try {
            tempFile = File.createTempFile("material-", "." + extension);
            file.transferTo(tempFile);

            String cosUrl = tryUploadToCos(cosKey, tempFile);
            if (StrUtil.isNotBlank(cosUrl)) {
                url = cosUrl;
                storagePath = cosKey;
            } else {
                storagePath = saveToLocal(loginUser.getId(), fileName, tempFile);
                url = LOCAL_URL_PREFIX + storagePath;
            }
        } catch (IOException e) {
            log.error("素材上传失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传失败，请稍后重试");
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }

        Material material = Material.builder()
                .userId(loginUser.getId())
                .url(url)
                .storagePath(storagePath)
                .originalName(StrUtil.sub(file.getOriginalFilename(), 0, 255))
                .contentType(file.getContentType())
                .size(file.getSize())
                .createTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        this.save(material);
        return toVO(material);
    }

    @Override
    public Page<MaterialVO> listMy(MaterialQueryRequest queryRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        MaterialQueryRequest query = queryRequest == null ? new MaterialQueryRequest() : queryRequest;
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);

        // 默认只看自己；管理员可通过 userId 查看指定用户
        Long targetUserId = loginUser.getId();
        if (isAdmin(loginUser) && query.getUserId() != null && query.getUserId() > 0) {
            targetUserId = query.getUserId();
        }
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("userId", targetUserId)
                .like("originalName", StrUtil.isBlank(query.getKeyword()) ? null : query.getKeyword().trim())
                .orderBy("createTime", false);

        Page<Material> materialPage = this.page(Page.of(pageNum, pageSize), wrapper);
        Page<MaterialVO> result = new Page<>(pageNum, pageSize, materialPage.getTotalRow());
        List<MaterialVO> records = materialPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAM_ERROR, "素材 ID 无效");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        Material material = this.getById(id);
        ThrowUtils.throwIf(material == null, ErrorCode.NOT_FOUND, "素材不存在或已删除");
        ThrowUtils.throwIf(!loginUser.getId().equals(material.getUserId()) && !isAdmin(loginUser),
                ErrorCode.NOT_AUTH_ERROR, "无权限删除该素材");
        // 被社区帖子当封面的素材不允许直接删，否则帖子封面会变成裂图
        long usedByPosts = communityPostMapper.countByCover(material.getUrl());
        ThrowUtils.throwIf(usedByPosts > 0, ErrorCode.OPERATION_ERROR,
                "该图片已被 " + usedByPosts + " 篇帖子用作封面，请先更换帖子封面再删除");

        this.removeById(id);
        // 尽力清理存储，失败只记日志（避免因为存储异常导致删除接口失败）
        cleanupStorage(material.getStoragePath());
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 校验文件类型与大小（博客 UploadService#validateFile 的等价实现）
     */
    private void validateFile(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAM_ERROR, "请选择要上传的图片");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE, ErrorCode.PARAM_ERROR, "图片不能超过 10MB");
        String contentType = file.getContentType();
        String extension = FileUtil.extName(file.getOriginalFilename());
        boolean typeOk = contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
        boolean extOk = StrUtil.isNotBlank(extension)
                && ALLOWED_EXT.contains(extension.toLowerCase(Locale.ROOT));
        ThrowUtils.throwIf(!typeOk && !extOk, ErrorCode.PARAM_ERROR, "仅支持 JPG / PNG / GIF / WebP 图片");
    }

    /**
     * 解析扩展名：优先取原文件名，取不到时按 MIME 兜底
     */
    private String resolveExtension(MultipartFile file) {
        String extension = FileUtil.extName(file.getOriginalFilename());
        if (StrUtil.isNotBlank(extension)) {
            return extension.toLowerCase(Locale.ROOT);
        }
        String contentType = StrUtil.blankToDefault(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (contentType.contains("png")) {
            return "png";
        }
        if (contentType.contains("gif")) {
            return "gif";
        }
        if (contentType.contains("webp")) {
            return "webp";
        }
        return "jpg";
    }

    /**
     * 尝试上传到 COS，失败返回 null（由调用方回退本地）
     */
    private String tryUploadToCos(String cosKey, File file) {
        try {
            String url = cosManager.uploadFile(cosKey, file);
            if (StrUtil.isBlank(url)) {
                log.warn("COS 上传返回空地址，回退本地存储");
            }
            return url;
        } catch (Exception e) {
            log.warn("COS 上传异常，回退本地存储: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存到本地兜底目录，返回相对路径 {userId}/{fileName}
     */
    private String saveToLocal(Long userId, String fileName, File source) throws IOException {
        Path dir = Paths.get(AppConstant.MATERIAL_ROOT_DIR, String.valueOf(userId))
                .toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return userId + "/" + fileName;
    }

    /**
     * 清理存储中的文件（COS 对象或本地文件）
     */
    private void cleanupStorage(String storagePath) {
        if (StrUtil.isBlank(storagePath)) {
            return;
        }
        try {
            if (storagePath.startsWith(COS_KEY_PREFIX)) {
                cosManager.deleteObject(storagePath);
                return;
            }
            Path file = Paths.get(AppConstant.MATERIAL_ROOT_DIR, storagePath).toAbsolutePath().normalize();
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.warn("素材存储清理失败（不影响数据库删除）: {} -> {}", storagePath, e.getMessage());
        }
    }

    private boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }

    private MaterialVO toVO(Material material) {
        MaterialVO vo = new MaterialVO();
        vo.setId(material.getId());
        vo.setUrl(material.getUrl());
        vo.setOriginalName(material.getOriginalName());
        vo.setContentType(material.getContentType());
        vo.setSize(material.getSize());
        vo.setCreateTime(material.getCreateTime());
        return vo;
    }
}
