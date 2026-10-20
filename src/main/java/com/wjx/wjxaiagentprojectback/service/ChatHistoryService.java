package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.chathistory.ChatHistoryUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.ChatHistory;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author wjx
 * @since 2026-09-06
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    public boolean savehistory(Long appId,String message,String messageType,Long userId);
    public boolean deletehistory(Long appId);

    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
    public Page<ChatHistory> listChatHistory(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser);

    Boolean Update(ChatHistoryUpdateRequest chatHistoryUpdateRequest);

    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 添加用户消息到对话历史
     * @param appId
     * @param usermessage
     * @param value
     * @param id
     */
    void addChatMessage(Long appId, String usermessage, String value, Long id);
}
