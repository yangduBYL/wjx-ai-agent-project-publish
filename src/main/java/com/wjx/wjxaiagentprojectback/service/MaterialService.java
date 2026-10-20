package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.wjx.wjxaiagentprojectback.damon.dto.material.MaterialQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.Material;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.MaterialVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材 服务层。
 *
 * <p>改写自个人博客系统 UploadService + Media 表：
 * 保留「校验类型/大小 → 唯一文件名 → 上传对象存储（失败回退本地）→ 落库返回 url」的主流程，
 * 新增按用户隔离、分页列表与删除（含存储清理）。</p>
 *
 * @author wjx
 * @since 2026-10-20
 */
public interface MaterialService extends IService<Material> {

    /**
     * 上传图片素材
     *
     * @param file      图片文件
     * @param loginUser 当前登录用户
     * @return 素材信息（url 可直接用作帖子封面）
     */
    MaterialVO upload(MultipartFile file, User loginUser);

    /**
     * 分页查询我的素材
     */
    Page<MaterialVO> listMy(MaterialQueryRequest queryRequest, User loginUser);

    /**
     * 删除素材（上传者本人或管理员），同时尽力清理存储中的文件
     */
    void delete(Long id, User loginUser);
}
