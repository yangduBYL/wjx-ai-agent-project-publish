package com.wjx.wjxaiagentprojectback.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.wjx.wjxaiagentprojectback.annotation.AuthCheck;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.wjx.wjxaiagentprojectback.damon.entity.ChatHistory;
import com.wjx.wjxaiagentprojectback.service.ChatHistoryService;

import javax.xml.transform.Result;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author wjx
 * @since 2026-09-06
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    /**
     * 保存对话历史。
     *
     * @param chatHistoryadd 对话历史
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("/save")
    public BaseResponse<Boolean> save(@RequestBody ChatHistoryAddRequest chatHistoryadd) {
        ThrowUtils.throwIf(chatHistoryadd==null,ErrorCode.PARAM_ERROR);
        boolean savehistory = chatHistoryService.savehistory(chatHistoryadd.getAppId(), chatHistoryadd.getMessage(), chatHistoryadd.getMessageType(), chatHistoryadd.getUserId());
        return ResultUtils.success(savehistory);

    }

    /**
     * 根据主键删除对话历史。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable("id") Long id) {
        return chatHistoryService.removeById(id);
    }

    /**
     * 根据主键更新对话历史。
     *
     * @param chatHistoryUpdateRequest 对话历史
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public BaseResponse<Boolean> update(@RequestBody ChatHistoryUpdateRequest chatHistoryUpdateRequest) {
        ThrowUtils.throwIf(chatHistoryUpdateRequest==null,ErrorCode.PARAM_ERROR);
        Boolean result=chatHistoryService.Update(chatHistoryUpdateRequest);
        return ResultUtils.success(result);
    }


    /**
     * 根据主键获取对话历史。
     *
     * @param id 对话历史主键
     * @return 对话历史详情
     */
    @GetMapping("/getInfo/{id}")
    public ChatHistory getInfo(@PathVariable("id") Long id) {
        return chatHistoryService.getById(id);
    }

    /**
     * 查询某个应用的对话历史。
     *
     * @param
     * @return 分页对象
     */
    @GetMapping("/page/app/history")
    //@RequestParam 显式指定 value 属性，这样 Spring 无需依赖反射即可匹配前端传入的参数：
    public BaseResponse<Page<ChatHistory>> pageChathistory(@RequestParam("appId") Long appId, @RequestParam(value = "lastCreateTime",required = false) LocalDateTime lastCreateTime,@RequestParam(value = "pageSize",defaultValue = "10") int pageSize, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> results = chatHistoryService.listChatHistory(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(results);
    }


    /**
     * 管理员查询所有对话历史。
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 分页对象
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllPageChatHistory(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest){
        //参数校验
        ThrowUtils.throwIf(chatHistoryQueryRequest==null, ErrorCode.PARAM_ERROR,"参数为空");
        //当前页面数
        int pageNum = chatHistoryQueryRequest.getPageNum();
        //页面中所展示的数据量
        int pageSize = chatHistoryQueryRequest.getPageSize();
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> chatHistoryPage = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(chatHistoryPage);
    }
}
