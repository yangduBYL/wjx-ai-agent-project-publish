package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.app.AppUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.App;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.ChatHistoryMessageTypeEnum;
import com.wjx.wjxaiagentprojectback.damon.vo.AppVo;
import com.wjx.wjxaiagentprojectback.damon.vo.App_UserVo;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author wjx
 * @since 2026-08-01
 */
public interface AppService extends IService<App>{
    /**
     * 添加应用
     * @param appAddRequest
     * @return
     */
    void add(AppAddRequest appAddRequest);

    /**
     * 根据id删除应用
     * @param id
     * @return
     */
    public boolean deleteById(Long id);

    /**
     * 更新应用
     * @param appUpdateRequest
     */
    void updateApp(AppUpdateRequest appUpdateRequest);

    /**
     *分页查询
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 根据id查询应用
     * @param records
     * @return
     */
    List<AppVo> getappVoList(List<App> records);

    /**
     * 返回脱敏后的信息
     * @param records
     * @return
     */
    List<App_UserVo> getappUserVo(List<App> records);


    /**
     * 用户分页查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getUserQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 优先级查询展示
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getpriortyQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * Flux流式编程
     * 作用实现打字机效果
     */
    Flux<String> chatToGencode(Long appId, String usermessage, User loginUser);

    /**
     * 应用部署
     *
     */
    String deploy(Long appId,User loginUser);
}
