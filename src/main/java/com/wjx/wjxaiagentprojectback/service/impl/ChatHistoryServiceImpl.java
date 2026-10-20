package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.App;
import com.wjx.wjxaiagentprojectback.damon.entity.ChatHistory;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.ChatHistoryMessageTypeEnum;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import com.wjx.wjxaiagentprojectback.mapper.ChatHistoryMapper;
import com.wjx.wjxaiagentprojectback.service.AppService;
import com.wjx.wjxaiagentprojectback.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 * @author wjx
 * @since 2026-09-06
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

    @Resource
    @Lazy
    private AppService appService;


    @Override
    public Boolean Update(ChatHistoryUpdateRequest chatHistoryUpdateRequest) {
        //校验消息参数
        ChatHistoryMessageTypeEnum enumByValue = ChatHistoryMessageTypeEnum.getEnumByValue(chatHistoryUpdateRequest.getMessageType());
        ThrowUtils.throwIf(enumByValue==null,ErrorCode.PARAM_ERROR,"消息类型不支持"+chatHistoryUpdateRequest.getMessageType());
        //保存数据到数据库
        ChatHistory chatHistory=ChatHistory.builder()
                .messageType(enumByValue.getValue())
                .message(chatHistoryUpdateRequest.getMessage())
                .id(chatHistoryUpdateRequest.getId())
                .appId(chatHistoryUpdateRequest.getAppId())
                .userId(chatHistoryUpdateRequest.getUserId())
                .createTime(chatHistoryUpdateRequest.getLastCreateTime())
                .build();
        return this.updateById(chatHistory);
    }

    @Override
    public boolean savehistory(Long appId,String message,String messageType,Long userId) {
        //判断是否为空
        ThrowUtils.throwIf(appId==null,ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(message==null,ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(messageType==null,ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(userId==null,ErrorCode.PARAM_ERROR);
        //校验消息参数
        ChatHistoryMessageTypeEnum chatHistoryMessageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(chatHistoryMessageTypeEnum==null,ErrorCode.PARAM_ERROR,"消息类型不支持"+messageType);
        //保存数据到数据库
        ChatHistory chatHistory=ChatHistory.builder()
                .appId(appId)
                .createTime(LocalDateTime.now())
                .message(message)
                .userId(userId)
//                .parentId()
                .messageType(messageType)
                .build();
        return this.save(chatHistory);
    }

    /**
     * 根据应用id来删除对话历史
     * @param appId
     * @return
     */
    @Override
    public boolean deletehistory(Long appId) {
        ThrowUtils.throwIf(appId==null,ErrorCode.PARAM_ERROR);
        QueryWrapper queryWrapper=QueryWrapper.create()
                .eq(ChatHistory::getAppId,appId);
        return this.remove(queryWrapper);
    }

    /**
     * 分页查询
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        //对查询请求参数进行空值校验，如果请求对象为空，则直接返回一个空的查询条件包装器，避免后续代码出现空指针异常
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        //sortField 排序字段
        String sortField = chatHistoryQueryRequest.getSortField();
        //sortOrder 排序顺序
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            //ToDo在 SQL 中lt对应 <（小于） 操作符。
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            //排序字段（前端传递的控制字段），升序
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    /**
     * 查询某个应用的对话历史
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    @Override
    public Page<ChatHistory> listChatHistory(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAM_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NOT_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }


    /**
     * 将对话历史从数据库中插入到redis中为ai提供对话记忆。
     * @param appId
     * @param chatMemory
     * @param maxCount
     * @return
     */
//    @Override
//    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
//        try {
//            //因为最新的那条用户消息，当前还在内存中（或者马上就要发送给AI），它根本不需要去数据库里捞。如果把它也捞出来放进上下文，就会导致最新的问题被重复发送了两次给 AI。
//            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
//            QueryWrapper queryWrapper = QueryWrapper.create()
//                    .eq(ChatHistory::getAppId, appId)
//                    .orderBy(ChatHistory::getCreateTime, false)
//                    .limit(1, maxCount);
//            List<ChatHistory> historyList = this.list(queryWrapper);
//            if (CollUtil.isEmpty(historyList)) {
//                return 0;
//            }
//            // 反转列表，确保按时间正序（老的在前，新的在后）
//            historyList = historyList.reversed();
//            // 按时间顺序添加到记忆中
//            int loadedCount = 0;
//            // 先清理历史缓存，防止重复加载
//            chatMemory.clear();
//            for (ChatHistory history : historyList) {
//                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
//                    chatMemory.add(UserMessage.from(history.getMessage()));
//                    loadedCount++;
//                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
//                    chatMemory.add(AiMessage.from(history.getMessage()));
//                    loadedCount++;
//                }
//            }
//            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
//            return loadedCount;
//        } catch (Exception e) {
//            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
//            // 加载失败不影响系统运行，只是没有历史上下文
//            return 0;
//        }
//    }

    /**
     * 将对话历史从数据库中插入到redis中为ai提供对话记忆。
     * @param appId
     * @param chatMemory
     * @param maxCount
     * @return
     */
    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

    /**
     * 添加用户消息到对话历史
     * @param appId
     * @param usermessage
     * @param value
     * @param id
     */
    @Override
    public void addChatMessage(Long appId, String usermessage, String value, Long id) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setMessage(usermessage);
        chatHistory.setMessageType(value);
        chatHistory.setUserId(id);
        this.save(chatHistory);
    }
}
