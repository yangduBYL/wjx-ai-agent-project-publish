package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.bean.BeanUtil;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjx.wjxaiagentprojectback.code.AiCodeGeneratorFacade;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import com.wjx.wjxaiagentprojectback.core.builder.VueProjectBuilder;
import com.wjx.wjxaiagentprojectback.core.handle.StreamHandlerExecutor;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.App;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.ChatHistoryMessageTypeEnum;
import com.wjx.wjxaiagentprojectback.damon.enums.CodeTypeEnum;
import com.wjx.wjxaiagentprojectback.damon.vo.AppVo;
import com.wjx.wjxaiagentprojectback.damon.vo.App_UserVo;
import com.wjx.wjxaiagentprojectback.damon.vo.UserVo;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.mapper.AppMapper;
import com.wjx.wjxaiagentprojectback.service.AppCoverService;
import com.wjx.wjxaiagentprojectback.service.AppService;
import com.wjx.wjxaiagentprojectback.service.ChatHistoryService;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * 应用 服务层实现。
 *
 * @author wjx
 * @since 2026-08-01
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{
    private static final String APP_GOOD_KEY = "app:good:";
    @Resource
    private UserService userService;
    @Autowired
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private AppCoverService appCoverService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 添加应用
     * @param appAddRequest
     */
    @Override
    public void add(AppAddRequest appAddRequest) {
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        //保存到数据库
        this.save(app);
    }

    /**
     * 根据id删除
     * 关联删除对话历史
     * @param id
     * @return
     */
    @Override
    public boolean deleteById(Long id) {
        ThrowUtils.throwIf(id==null,ErrorCode.PARAM_ERROR);
        //删除对话历史
        try{
            chatHistoryService.deletehistory(id);
        }catch (Exception e){
            //输出错误日志
            log.error("删除对话历史失败",e.getMessage());
        }
        //删除应用
        return super.removeById(id);
    }

    /**
     * 更新应用
     * @param appUpdateRequest
     */
    @Override
    public void updateApp(AppUpdateRequest appUpdateRequest) {
        //先查一遍数据库，在更新
        Long id = appUpdateRequest.getId();
        App app = this.mapper.selectOneById(id);
        if (app == null){
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        BeanUtil.copyProperties(appUpdateRequest, app);
        this.updateById(app);
    }

    /**
     * 分页查询,查询条件的构建
     * @param appQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        //sortField：指定排序的字段名
        String sortField=appQueryRequest.getSortField();
        //ortOrder：指定排序的方向
        String sortOrder=appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", appQueryRequest.getId())
                .eq("deployKey", appQueryRequest.getDeployKey())
                .eq("userId", appQueryRequest.getUserId())
                .eq("codeGenType", appQueryRequest.getCodeGenType())
                .like("deployedTime", appQueryRequest.getDeployedTime())
                .like("cover", appQueryRequest.getCover())
                .like("appName", appQueryRequest.getAppName())
                .like("initprompt", appQueryRequest.getInitprompt())
                .ge("priority", appQueryRequest.getPriority())
                .orderBy(sortField,"ascend".equals(sortOrder));
    }

    /**
     * 返回脱敏后的信息
     * @param records
     * @return
     */
    @Override
    public List<AppVo> getappVoList(List<App> records) {
        List<AppVo> appVoList =new ArrayList<>();
        for (App app : records) {
            AppVo appVo = new AppVo();
            BeanUtil.copyProperties(app, appVo);
            appVoList.add(appVo);
        }
        return appVoList;
    }

    /**
     * 返回脱敏后的信息app_uservo
     * @param records
     * @return
     */
    @Override
    public List<App_UserVo> getappUserVo(List<App> records) {
        List<App_UserVo> appUserVoList =new ArrayList<>();
        for (App app : records) {
            //app信息的封装
            App_UserVo appUserVo = new App_UserVo();
            BeanUtil.copyProperties(app, appUserVo);

            //用户信息的封装
            User user = this.userService.getMapper().selectOneById(app.getUserId());
            UserVo userVo = new UserVo();
            BeanUtil.copyProperties(user, userVo);
            appUserVo.setUservo(userVo);

            appUserVoList.add(appUserVo);
        }
        return appUserVoList;
    }

    /**
     * 用户分页查询条件
     * @param appQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getUserQueryWrapper(AppQueryRequest appQueryRequest) {
        //sortField：指定排序的字段名
        String sortField=appQueryRequest.getSortField();
        //ortOrder：指定排序的方向
        String sortOrder=appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("userId", appQueryRequest.getUserId())
                .orderBy(sortField,"ascend".equals(sortOrder));
    }

    /**
     * 优先级查询展示
     * @param appQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getpriortyQueryWrapper(AppQueryRequest appQueryRequest) {
        //sortField：指定排序的字段名
        String sortField=appQueryRequest.getSortField();
        //ortOrder：指定排序的方向
        String sortOrder=appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                //展示大于该优先级的数据
                .ge("priority", appQueryRequest.getPriority())
                .orderBy(sortField,"ascend".equals(sortOrder));
    }

    /**
     * ai流式打字机模拟对话
     * @param appId
     * @param usermessage
     * @param loginUser
     * @return
     */
    @Override
    public Flux<String> chatToGencode(Long appId, String usermessage, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAM_ERROR,"应用id错误");
        ThrowUtils.throwIf(StrUtil.isBlank(usermessage), ErrorCode.PARAM_ERROR,"用户提示词为空");
        //2.查询应用信息
        App app = this.getById(appId);
        if(app == null){
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        //3.权限校验，用户只能用自己的登录id去关联对应的用户appid
        if(!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NOT_AUTH_ERROR,"用户id与appid不一致，无权限访问");
        }
        //4。获取对应的app的代码生成类型，在调用保存器来保存
        String codeGenType = app.getCodeGenType();
        CodeTypeEnum codeTypeEnum= CodeTypeEnum.getValueCode(codeGenType);
        if(codeTypeEnum == null){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"代码生成类型错误");
        }
        // 5. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, usermessage, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
         // 6. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(usermessage, codeTypeEnum, appId);
        // 7. 收集 AI 响应内容并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeTypeEnum);

    }

    /**作用：为所生成的代码保存文件起为一名称
     * 应用部署
     * @param appId
     * @param loginUser
     * @return
     */
    @Override
    public String deploy(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        //对Vue项目特殊处理
        // 7. Vue 项目特殊处理：执行构建
        CodeTypeEnum codeTypeEnum = CodeTypeEnum.getValueCode(codeGenType);
        if (codeTypeEnum == CodeTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        //        // 10. 返回可访问的 URL
        //        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11. 异步生成截图并更新应用封面
        // deployKey 已落库，封面服务会按「已部署」规则截部署地址的页面，
        // HTML 单文件 / 多文件 / Vue 工程三种类型走的是同一套逻辑
        appCoverService.refreshCoverAsync(appId);
        return appDeployUrl;

    }


}
