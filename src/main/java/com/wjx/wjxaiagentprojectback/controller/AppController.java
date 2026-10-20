package com.wjx.wjxaiagentprojectback.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.wjx.wjxaiagentprojectback.annotation.AuthCheck;
import com.wjx.wjxaiagentprojectback.annotation.RateLimit;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.DeleteRequest;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.constant.AppConstant;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppDeployRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.RateLimitType;
import com.wjx.wjxaiagentprojectback.damon.vo.AppVo;
import com.wjx.wjxaiagentprojectback.damon.vo.App_UserVo;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.service.ProjectDownloadService;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.wjx.wjxaiagentprojectback.damon.entity.App;
import com.wjx.wjxaiagentprojectback.service.AppCoverService;
import com.wjx.wjxaiagentprojectback.service.AppService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author wjx
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;
    @Resource
    private AppCoverService appCoverService;
    @Autowired
    private UserService userService;

    @Resource
    private ProjectDownloadService projectDownloadService;

    /**
     * 刷新应用封面接口（重新截图并写回 cover）
     * 管理员可刷新任意应用，普通用户只能刷新自己的应用。
     * 已部署的应用截部署地址，未部署的应用截静态预览地址。
     *
     * @param appId   应用 ID
     * @param request 请求（取登录用户）
     * @return 新的封面地址
     */
    @PostMapping("/cover/refresh")
    public BaseResponse<String> refreshCover(@RequestParam("appId") Long appId, HttpServletRequest request) {
        // 1. 参数与登录校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 权限校验：管理员或应用创建者本人
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !app.getUserId().equals(loginUser.getId()),
                ErrorCode.NOT_AUTH_ERROR, "无权限刷新该应用封面");
        // 3. 同步截图并返回新的封面地址
        return ResultUtils.success(appCoverService.refreshCoverNow(appId));
    }

    /**
     * 部署应用接口
     * @param appDeployRequest
     * @param request
     * @return
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deploy(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        //1.校验参数
        ThrowUtils.throwIf(appDeployRequest==null, ErrorCode.PARAM_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId==null, ErrorCode.PARAM_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser==null, ErrorCode.PARAM_ERROR,"用户信息为空");
        //2.调用服务层
        String deployUrl = appService.deploy(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     *ai流式输出接口
     * @param appId
     * @param message
     * @param request
     * @return
     */

    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER,rate = 5,rateInterval = 60,message = "AI对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam("appId") Long appId,
                                                       @RequestParam("message") String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAM_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGencode(appId, message, loginUser);
        // 转换为 ServerSentEvent 格式
        return contentFlux
                .map(chunk -> {
                    // 将内容包装成JSON对象
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }


    /**
     * 添加应用。
     *
     * @param appAddRequest 应用请求
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/add")
    public BaseResponse<String> add(@RequestBody AppAddRequest appAddRequest) {
       ThrowUtils.throwIf(appAddRequest==null, ErrorCode.PARAM_ERROR);
       appService.add(appAddRequest);
       return ResultUtils.success("添加应用成功");
    }

    /**
     * 根据主键删除应用。
     *
     * @param deleteRequest 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("delete")
    public BaseResponse<String> delete(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest==null, ErrorCode.PARAM_ERROR);
        boolean b = appService.deleteById(deleteRequest.getId());
        return ResultUtils.success(b?"删除成功":"删除失败");

    }

    /**
     * 根据主键更新应用。
     *
     * @param
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public BaseResponse<String> update(@RequestBody AppUpdateRequest appUpdateRequest) {
        ThrowUtils.throwIf(appUpdateRequest==null, ErrorCode.PARAM_ERROR);
        appService.updateApp(appUpdateRequest);
        return ResultUtils.success("更新应用成功");
    }

    /**
     * 分页查询用户
     */
    @PostMapping("/list/page/app")
    public BaseResponse<Page<AppVo>> listAppVoByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest==null, ErrorCode.PARAM_ERROR);
        //获取当前页号
        long pageNum = appQueryRequest.getPageNum();
        //获取每页展示数据大小
        long pageSize =appQueryRequest.getPageSize();
        //获取分页查询结果
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getQueryWrapper(appQueryRequest));
        //数据脱敏
        //1.新建                                                         getTotalRow()获取总行数
        Page<AppVo> appVoPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        //2.数据脱敏处理                                      获得总数居
        List<AppVo> appVoList = appService.getappVoList(appPage.getRecords());
        //3.设置数据
        appVoPage.setRecords(appVoList);
        //4.返回结果
        return ResultUtils.success(appVoPage);
    }

    /**
     * 通过id获取应用详情
     *
     * @param id 应用主键
     * @return 应用详情
     */
    @GetMapping("/getAppVoId")
    public App getappById(@RequestParam("id") Long id) {
        ThrowUtils.throwIf(id==null, ErrorCode.PARAM_ERROR);
        return appService.getById(id);
    }

    /**
     * 分页查询(Admin用户)
     * @param
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page/app_user/admin")
    public BaseResponse<Page<App_UserVo>> listAppVoByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest==null, ErrorCode.PARAM_ERROR);
        //复用方法
        BaseResponse<Page<AppVo>> pageBaseResponse = listAppVoByPage(appQueryRequest);
        //获取当前页号
        long pageNum = appQueryRequest.getPageNum();
        //获取每页展示数据大小
        long pageSize =appQueryRequest.getPageSize();
        //获取分页查询结果
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getQueryWrapper(appQueryRequest));
//        //数据脱敏
        //1.新建                                                         getTotalRow()获取总行数
        Page<App_UserVo> appUserVoPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        //2.数据脱敏处理                                      获得总数居
        List<App_UserVo> app_userVoList = appService.getappUserVo(appPage.getRecords());
        //3.设置数据
        appUserVoPage.setRecords(app_userVoList);
        //4.返回结果
        return ResultUtils.success(appUserVoPage);
    }

    /**
     * 用户分页查询,该用户登陆后，只能查询到与他用户userid相关联的app信息
     */
    @PostMapping("/list/page/app_user/user")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<App_UserVo>> listAppVoByPageByUser(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest==null, ErrorCode.PARAM_ERROR);
        if(appQueryRequest.getUserId()==null){
            //获取登录用户的id
            appQueryRequest.setUserId(userService.getLoginUser(request).getId());
        }
        //获取当前页号
        long pageNum = appQueryRequest.getPageNum();
        //获取每页展示数据大小
        long pageSize=appQueryRequest.getPageSize();
        //获取分页查询结果
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getUserQueryWrapper(appQueryRequest));
        //数据脱敏
        //1.新建                                                         getTotalRow()获取总行数
        Page<App_UserVo> appUserVoPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        //2.数据脱敏处理                                      获得总数居
        List<App_UserVo> app_userVoList = appService.getappUserVo(appPage.getRecords());
        //3.设置数据
        appUserVoPage.setRecords(app_userVoList);
        //4.返回结果
        return ResultUtils.success(appUserVoPage);
    }

    /**根据优先级来优先展示对应的app信息
     * 分页查询
     * @param appQueryRequest
     * @return
     */
    @PostMapping("/list/page/GoodApp")
    /**
     * 这里使用了 SpEL（Spring Expression Language） 表达式：
     * T(类名）：用于调用静态方法，生成缓存key
     * #参数名：用于引用方法参数
     * condition：设置缓存条件，只有前10 页才会被缓存
     * 建议了解下缓存注解的工作原理，执行流程如下：
     * 1.方法执行前：Spring 根据 key 表达式生成缓存键
     * 2. 缓存检查：检查 Redis 中是否存在该键对应的缓存数据
     * 3.缓存命中：如果存在且未过期，直接返回缓存数据，不执行方法
     * 4. 缓存未命中：如果不存在，执行方法获取结果，并将结果存储到 Redis 中
     * 5.返回结果：返回方法执行结果
     */
    @Cacheable(
            value="good_app_page",
            key = "T(com.wjx.wjxaiagentprojectback.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum<=10"
    )
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<App_UserVo>> listGoodApp(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest==null, ErrorCode.PARAM_ERROR);
        //获取当前页号
        long pageNum = appQueryRequest.getPageNum();
        //获取每页展示数据大小
        long pageSize=appQueryRequest.getPageSize();
        //获取分页查询结果,根据优先级优先展示app数据
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getpriortyQueryWrapper(appQueryRequest));
        //数据脱敏
        //1.新建                                                         getTotalRow()获取总行数
        Page<App_UserVo> appUserVoPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        //2.数据脱敏处理                                      获得总数居
        List<App_UserVo> app_userVoList = appService.getappUserVo(appPage.getRecords());
        //3.设置数据
        appUserVoPage.setRecords(app_userVoList);
        //4.返回结果
        return ResultUtils.success(appUserVoPage);
    }



    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable("appId") Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }

}